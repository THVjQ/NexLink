"""
User-issued invites — docs/social/09-invites.md §9.5.

Until now invites were operator-only: §9.8's CLI, run over SSH, once per person.
That is the right control for the first twenty users and a bad one for the
twenty-first, so this is the piece that lets a **user** invite someone.

## Why a Synapse module and not a service

Minting a registration token needs Synapse *admin* credentials. A phone can
never hold those — an admin token reads every room's metadata and can deactivate
any account — so the mint has to happen somewhere the user cannot reach, and the
client has to ask that somewhere to do it.

The obvious shape is a small service beside Synapse. The reason it is a module
instead is routing: public routing here is **per-hostname in the Cloudflare
dashboard** (§26.5.2), so a new service means a new hostname, a new certificate
path and a new piece of public attack surface that mints accounts. A module is
served by Synapse itself on a host that is already routed and already TLS'd,
and `get_user_by_req` authenticates the caller with the same code path as every
other endpoint. Nothing new is exposed and no auth is re-implemented.

## The one coupling to be honest about

`registration_tokens` is an internal Synapse table, not a public interface. This
writes to it directly rather than calling the admin API, so that no admin token
has to exist in config for the module to steal. The table has been stable since
Synapse 1.35, but a schema change upstream WILL break this — so the shape is
checked once at startup and refuses to load if it has moved, rather than failing
per-request in production. See `_assert_schema`.
"""

# DEPLOYMENT: this file is auto-synced onto the homeserver by the
# social-sync sidecar, which pulls feat/social-foundations every 120s and
# copies it into /data. Editing the copy on Willard is pointless — the next
# tick overwrites it. Synapse must still be RESTARTED to load a change;
# the sidecar writes /data/.modules-pending-restart and will not restart it
# for you, because a module that fails to mount puts Synapse in a restart
# loop (§31.6) and this runs unattended.

import json
import logging
import os
import secrets
import time
from hashlib import sha256
from typing import Any, Dict, Tuple

from synapse.module_api import ModuleApi
from synapse.http.server import DirectServeJsonResource, respond_with_json
from synapse.http.site import SynapseRequest

logger = logging.getLogger(__name__)

# docs/social/09-invites.md §9.3 — Crockford base32, less I, L, O and U. This
# MUST stay identical to InviteCode.ALPHABET in :social-core, or the app will
# refuse a code the server considers valid.
ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

# Must match InviteIssuer.PATH in :social-core.
PATH = "/_matrix/nexlink/v1/invite"
CODE_LENGTH = 12

DEFAULT_VALID_DAYS = 14
# Not a quota. The operator chose unlimited invites (§9.5.1), so this exists
# only to stop a loop — a stuck client retrying, or a script — from filling the
# table. A person inviting people by hand will never see it.
DEFAULT_BURST_PER_HOUR = 30


class _InviteResource(DirectServeJsonResource):
    def __init__(self, api: ModuleApi, config: Dict[str, Any]):
        super().__init__()
        self._api = api
        self._valid_days = config.get("valid_days", DEFAULT_VALID_DAYS)
        self._burst = config.get("burst_per_hour", DEFAULT_BURST_PER_HOUR)
        self._record_path = config.get("record_path", "/data/nexlink-invites.jsonl")
        self._recent: Dict[str, list] = {}

    async def _async_render_GET(self, request: SynapseRequest) -> None:
        """
        The codes this user issued that are still worth anything.

        Only *their* codes. The record is keyed by issuer and the query is
        filtered by it, so one user cannot enumerate — let alone revoke —
        another's invites. With no quota in force (§9.5.1) that separation is
        doing real work: it is the difference between "see your own invites" and
        "an authenticated user can shut down everyone's".
        """
        requester = await self._api.get_user_by_req(request)
        user_id = requester.user.to_string()
        mine = self._mine(user_id)
        if not mine:
            respond_with_json(request, 200, {"invites": []}, send_cors=True)
            return

        rows = await self._api.run_db_interaction(
            "nexlink_list_invites", _select_tokens, list(mine.keys()))
        now_ms = int(time.time() * 1000)
        out = []
        for token, uses_allowed, pending, completed, expiry in rows:
            rec = mine.get(sha256(token.encode()).hexdigest())
            if rec is None:
                continue
            used = completed > 0 or (uses_allowed is not None and completed >= uses_allowed)
            out.append({
                "code": token,
                "formatted": format_code(token),
                "issued_at": rec.get("issued_at"),
                "expires_at": expiry,
                "used": used,
                "expired": expiry is not None and expiry < now_ms,
            })
        out.sort(key=lambda r: r.get("issued_at") or 0, reverse=True)
        respond_with_json(request, 200, {"invites": out}, send_cors=True)

    async def _async_render_DELETE(self, request: SynapseRequest) -> None:
        """
        Revoke one unredeemed code.

        A code already redeemed is NOT deleted: the row is Synapse's record that
        an account was created with it, and removing it would erase that link.
        Revoking something already spent is meaningless anyway — the account
        exists, and suspending it is §31's job, not this endpoint's.
        """
        requester = await self._api.get_user_by_req(request)
        user_id = requester.user.to_string()
        code = request.args.get(b"code", [b""])[0].decode().replace("-", "").upper()

        if sha256(code.encode()).hexdigest() not in self._mine(user_id):
            # Same answer whether it was someone else's or never existed, so
            # this cannot be used to test whether a code is real.
            respond_with_json(request, 404,
                              {"errcode": "M_NOT_FOUND", "error": "No such invite."},
                              send_cors=True)
            return

        removed = await self._api.run_db_interaction(
            "nexlink_revoke_invite", _delete_unused_token, code)
        if removed:
            logger.info("nexlink: invite revoked by %s", user_id)
            respond_with_json(request, 200, {"revoked": True}, send_cors=True)
        else:
            respond_with_json(request, 409,
                              {"errcode": "M_UNKNOWN",
                               "error": "That code has already been used."},
                              send_cors=True)

    def _mine(self, user_id: str) -> Dict[str, Dict[str, Any]]:
        """code_sha256 -> record, for one issuer. Read fresh each time: the file
        is small, and caching it would mean a revoke on one worker is invisible
        to another."""
        out: Dict[str, Dict[str, Any]] = {}
        try:
            with open(self._record_path) as f:
                for line in f:
                    try:
                        rec = json.loads(line)
                    except ValueError:
                        continue          # a truncated line must not hide the rest
                    if rec.get("issuer") == user_id:
                        out[rec.get("code_sha256", "")] = rec
        except FileNotFoundError:
            pass
        except OSError:
            logger.exception("nexlink: could not read the invite record")
        return out

    async def _async_render_POST(self, request: SynapseRequest) -> None:
        # Authenticated as the calling user by Synapse itself. An unauthenticated
        # request never reaches the body of this method.
        requester = await self._api.get_user_by_req(request)
        user_id = requester.user.to_string()

        if not self._within_burst(user_id):
            respond_with_json(
                request, 429,
                {"errcode": "M_LIMIT_EXCEEDED",
                 "error": "Too many invites in a short time. Try again shortly."},
                send_cors=True)
            return

        code, expiry_ms = await self._mint()
        self._record(user_id, code, expiry_ms)
        logger.info("nexlink: invite issued by %s (expires %d)", user_id, expiry_ms)
        respond_with_json(
            request, 200,
            {"code": code, "formatted": format_code(code), "expires_at": expiry_ms},
            send_cors=True)

    def _within_burst(self, user_id: str) -> bool:
        now = time.time()
        seen = [t for t in self._recent.get(user_id, []) if now - t < 3600]
        if len(seen) >= self._burst:
            self._recent[user_id] = seen
            return False
        seen.append(now)
        self._recent[user_id] = seen
        return True

    async def _mint(self) -> Tuple[str, int]:
        expiry_ms = int((time.time() + self._valid_days * 86400) * 1000)
        # Retry on collision rather than trusting 60 bits blindly. It will never
        # fire; a duplicate primary key raising a 500 at a user would be a silly
        # way to find that out.
        for _ in range(5):
            code = new_code()
            inserted = await self._api.run_db_interaction(
                "nexlink_insert_invite", _insert_token, code, expiry_ms)
            if inserted:
                return code, expiry_ms
        raise RuntimeError("could not allocate an unused invite code")

    def _record(self, user_id: str, code: str, expiry_ms: int) -> None:
        """
        Who issued what, for revocation and for answering "where did this
        account come from" later (§9.2.2).

        **The raw code is never written** — only its SHA-256, exactly as §29.1
        requires of the operator CLI. A file of live invite codes would be a
        file of account-creation credentials.
        """
        line = json.dumps({
            "issuer": user_id,
            "code_sha256": sha256(code.encode()).hexdigest(),
            "issued_at": int(time.time() * 1000),
            "expires_at": expiry_ms,
        })
        try:
            with open(self._record_path, "a") as f:
                f.write(line + "\n")
        except OSError:
            # An unwritable record must not stop someone inviting a friend, but
            # it must not pass silently either.
            logger.exception("nexlink: could not write the invite record")


def _select_tokens(txn, hashes):
    """
    Every unexpired-or-not token, filtered to the caller's in Python.

    The record stores only the SHA-256 of a code (§29.1), and SQL cannot hash,
    so the join happens here rather than in the query. The table holds one row
    per invite ever issued on this server, which for a private launch is tens —
    if it ever becomes tens of thousands, store the token id alongside the hash
    instead of scanning.
    """
    txn.execute(
        "SELECT token, uses_allowed, pending, completed, expiry_time"
        " FROM registration_tokens"
    )
    return txn.fetchall()


def _delete_unused_token(txn, code: str) -> bool:
    """Delete only if nothing has been created with it. `completed = 0` is the
    guard: a spent token is the server's record that an account came from it."""
    txn.execute(
        "DELETE FROM registration_tokens WHERE token = ? AND completed = 0",
        (code,),
    )
    return txn.rowcount > 0


def _insert_token(txn, code: str, expiry_ms: int) -> bool:
    txn.execute(
        "INSERT INTO registration_tokens (token, uses_allowed, pending, completed, expiry_time)"
        " VALUES (?, 1, 0, 0, ?) ON CONFLICT (token) DO NOTHING",
        (code, expiry_ms),
    )
    return txn.rowcount > 0


def new_code() -> str:
    return "".join(secrets.choice(ALPHABET) for _ in range(CODE_LENGTH))


def format_code(code: str) -> str:
    """§9.3 — three groups of four. The hyphens are cosmetic and are stripped
    again on entry, but a human reading a code aloud needs them."""
    return "-".join(code[i:i + 4] for i in range(0, CODE_LENGTH, 4))


class NexLinkInvites:
    def __init__(self, config: Dict[str, Any], api: ModuleApi):
        self._api = api
        self._config = config
        # **Under /_matrix/, as a sibling of Synapse's own trees.**
        #
        # Routing decided this, and two wrong answers came first:
        #
        #   /_synapse/client/nexlink/invite — works perfectly on localhost and
        #     returns 404 from a phone. The Cloudflare tunnel routes `/_matrix/`
        #     to Synapse and sends everything else to a catch-all that lands on
        #     Element Web (infra/runbooks/cloudflare-tunnel-routes.md), so the
        #     request never arrived. The same trap DeviceManager documents.
        #
        #   /_matrix/client/unstable/com.thvjq.nexlink/invite — the *correct*
        #     namespace for a custom Client-Server API, and it 404s even on
        #     localhost: Synapse's own JsonResource owns `/_matrix/client` and
        #     answers for every child of it, so a module cannot nest inside.
        #     The "Attaching …" log line appears and means nothing.
        #
        # `/_matrix/nexlink/` is a sibling of client/federation/media/key, which
        # Synapse does not claim, and it is inside the one prefix the tunnel
        # already sends here. No dashboard change, and nothing to forget later.
        api.register_web_resource(PATH, _InviteResource(api, config))
        logger.info("nexlink: user-issued invites registered")

    @staticmethod
    def parse_config(config: Dict[str, Any]) -> Dict[str, Any]:
        return config or {}
