"""
The operator's admin console — docs/social/31-moderation.md §31.6.

`tools/moderate.sh` covers this from a terminal. This is the same thing in a
browser, because §31 says these tools get used at 3 a.m. and under stress, and
"ssh in and compose a curl" is not a moderation surface.

## Why this is a module and not a web app

`/_synapse/admin/*` is denied at the Cloudflare edge (§26.5.2) and must stay
that way: it is an unauthenticated-looking surface that can deactivate any
account. A separate admin service would need its own public hostname, its own
certificate, and — worst — its own copy of an admin token sitting in a config
file somewhere.

This holds **no credential at all**. The page is static markup; every call that
touches data requires the caller's own Matrix access token, is checked against
`is_user_admin`, and is proxied to Synapse on localhost. Log out and nothing
remains. The admin API stays unreachable from the internet; this is a narrow,
authenticated door beside it.

## What that means for exposure

The HTML is served publicly, the way Element Web is. It is inert: without an
admin token every endpoint answers 403. The login form posts to Synapse's own
`/login`, which has Synapse's own rate limiting in front of it.
"""

# DEPLOYMENT: auto-synced by the social-sync sidecar every 120s. Edit here,
# not on Willard. Synapse must be RESTARTED to load a change.

import json
import logging
import os
from typing import Any, Dict, Optional

from synapse.module_api import ModuleApi
from synapse.http.server import DirectServeJsonResource, respond_with_json
from synapse.http.site import SynapseRequest
from twisted.web.resource import Resource
from twisted.web.server import NOT_DONE_YET

logger = logging.getLogger(__name__)

# The page and the API live on SEPARATE subtrees, and that is not cosmetic.
#
# Registering the page as a leaf at /_matrix/nexlink/admin and the endpoints at
# /_matrix/nexlink/admin/api/... put children underneath a leaf, which
# `create_resource_tree` cannot express: it raised
#   KeyError: "<_Page object>-b'api'"
# during `start_listening` — so Synapse did not fail to load the module, it
# failed to BOOT, and went into a restart loop taking the homeserver with it.
# Found the only way it can be: by restarting a live server. See §31.6.
PAGE = "/_matrix/nexlink/console"
API = "/_matrix/nexlink/v1/admin"
LOCAL = "http://localhost:8008"


class _Page(Resource):
    """The console itself. Static, and deliberately so — it carries no secret."""

    isLeaf = True

    def __init__(self, path: str):
        super().__init__()
        self._path = path

    def render_GET(self, request):
        try:
            with open(self._path, "rb") as f:
                body = f.read()
        except OSError:
            logger.exception("nexlink-admin: could not read the console page")
            body = b"<h1>The admin console page is missing from this server.</h1>"
        request.setHeader(b"Content-Type", b"text/html; charset=utf-8")
        # It is an admin tool; it should never be cached anywhere.
        request.setHeader(b"Cache-Control", b"no-store")
        request.setHeader(b"X-Frame-Options", b"DENY")
        request.setHeader(b"Referrer-Policy", b"no-referrer")
        return body


class _AdminJson(DirectServeJsonResource):
    """
    Base for every data endpoint: authenticate, insist on admin, then act.

    The token is taken from the request rather than from `get_user_by_req`'s
    Requester, because the proxied admin calls need the *caller's* credential.
    That is the point — this process never has an admin token of its own, so it
    can only ever do what the person signed in could already do.
    """

    def __init__(self, api: ModuleApi):
        super().__init__()
        self._api = api

    async def _require_admin(self, request: SynapseRequest) -> Optional[str]:
        requester = await self._api.get_user_by_req(request)
        user_id = requester.user.to_string()
        if not await self._api.is_user_admin(user_id):
            respond_with_json(request, 403,
                              {"errcode": "M_FORBIDDEN",
                               "error": "This account is not a server admin."},
                              send_cors=True)
            return None
        return user_id

    def _token(self, request: SynapseRequest) -> str:
        raw = request.requestHeaders.getRawHeaders(b"Authorization") or []
        for h in raw:
            v = h.decode()
            if v.lower().startswith("bearer "):
                return v[7:]
        return ""

    async def _admin_api(self, request, method: str, path: str,
                         body: Optional[Dict[str, Any]] = None) -> Any:
        """Proxy to the admin API on localhost, as the caller."""
        headers = {"Authorization": [f"Bearer {self._token(request)}"]}
        url = LOCAL + path
        if method == "GET":
            return await self._api.http_client.get_json(url, headers=headers)
        if method == "DELETE":
            # SimpleHttpClient has no delete_json; request() is the general form.
            resp = await self._api.http_client.request(
                "DELETE", url, headers=headers)
            return {"ok": resp.code < 300}
        return await self._api.http_client.post_json_get_json(
            url, body or {}, headers=headers)


class _Reports(_AdminJson):
    async def _async_render_GET(self, request: SynapseRequest) -> None:
        if await self._require_admin(request) is None:
            return
        data = await self._admin_api(
            request, "GET", "/_synapse/admin/v1/event_reports?limit=100&dir=b")
        respond_with_json(request, 200, data, send_cors=True)


class _User(_AdminJson):
    async def _async_render_GET(self, request: SynapseRequest) -> None:
        if await self._require_admin(request) is None:
            return
        who = request.args.get(b"id", [b""])[0].decode()
        if not who:
            respond_with_json(request, 400,
                              {"errcode": "M_MISSING_PARAM", "error": "No user given."},
                              send_cors=True)
            return
        data = await self._admin_api(
            request, "GET", f"/_synapse/admin/v2/users/{who}")
        respond_with_json(request, 200, data, send_cors=True)


class _Invites(_AdminJson):
    async def _async_render_GET(self, request: SynapseRequest) -> None:
        if await self._require_admin(request) is None:
            return
        data = await self._admin_api(
            request, "GET", "/_synapse/admin/v1/registration_tokens")
        respond_with_json(request, 200, data, send_cors=True)


class _Action(_AdminJson):
    """
    The things that change something.

    An allowlist, not a path passthrough: a proxy that forwards any admin path
    the browser names is not a narrower surface than exposing the admin API, it
    IS exposing the admin API.
    """

    async def _async_render_POST(self, request: SynapseRequest) -> None:
        if await self._require_admin(request) is None:
            return
        try:
            body = json.loads(request.content.read().decode())
        except ValueError:
            respond_with_json(request, 400,
                              {"errcode": "M_NOT_JSON", "error": "Bad body."},
                              send_cors=True)
            return

        action = body.get("action")
        target = body.get("user") or ""
        try:
            if action == "suspend":
                out = await self._admin_api(
                    request, "PUT", f"/_synapse/admin/v1/suspend/{target}",
                    {"suspend": bool(body.get("suspend", True))})
            elif action == "deactivate":
                out = await self._admin_api(
                    request, "POST", f"/_synapse/admin/v1/deactivate/{target}",
                    {"erase": True})
            elif action == "revoke":
                code = (body.get("code") or "").replace("-", "").upper()
                out = await self._admin_api(
                    request, "DELETE",
                    f"/_synapse/admin/v1/registration_tokens/{code}")
            else:
                respond_with_json(request, 400,
                                  {"errcode": "M_UNRECOGNIZED",
                                   "error": "Unknown action."}, send_cors=True)
                return
        except Exception as e:                        # noqa: BLE001
            logger.exception("nexlink-admin: %s failed", action)
            respond_with_json(request, 502,
                              {"errcode": "M_UNKNOWN", "error": str(e)},
                              send_cors=True)
            return

        logger.info("nexlink-admin: %s performed", action)
        respond_with_json(request, 200, {"ok": True, "result": out}, send_cors=True)


class NexLinkAdmin:
    def __init__(self, config: Dict[str, Any], api: ModuleApi):
        page = config.get("page_path", "/data/nexlink_admin.html")
        api.register_web_resource(PAGE, _Page(page))
        api.register_web_resource(API + "/reports", _Reports(api))
        api.register_web_resource(API + "/user", _User(api))
        api.register_web_resource(API + "/invites", _Invites(api))
        api.register_web_resource(API + "/action", _Action(api))
        logger.info("nexlink-admin: console registered at %s", PAGE)

    @staticmethod
    def parse_config(config: Dict[str, Any]) -> Dict[str, Any]:
        return config or {}
