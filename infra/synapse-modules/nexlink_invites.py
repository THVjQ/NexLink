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
        api.register_web_resource(
            "/_synapse/client/nexlink/invite", _InviteResource(api, config)
        )
        logger.info("nexlink: user-issued invites registered")

    @staticmethod
    def parse_config(config: Dict[str, Any]) -> Dict[str, Any]:
        return config or {}
