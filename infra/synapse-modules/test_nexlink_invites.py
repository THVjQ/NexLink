"""
Unit tests for the invite module — run on the desktop, not in Synapse.

Synapse is not installed here and installing it to test 40 lines of logic would
be its own kind of mistake, so the three symbols the module imports are stubbed.
That is honest about what these tests cover: the code format, the burst guard,
the SQL, and the record. Whether the module *loads* inside Synapse is a
different question and is answered at deploy time by watching it start.

    python3 infra/synapse-modules/test_nexlink_invites.py
"""
import json
import os
import sys
import tempfile
import types
import unittest

# ---- stub the Synapse surface the module imports --------------------------
mod_api = types.ModuleType("synapse.module_api")
mod_api.ModuleApi = object
http_server = types.ModuleType("synapse.http.server")
http_server.DirectServeJsonResource = type("DirectServeJsonResource", (), {"__init__": lambda self: None})
http_server.respond_with_json = lambda *a, **k: None
http_site = types.ModuleType("synapse.http.site")
http_site.SynapseRequest = object
synapse = types.ModuleType("synapse")
synapse.module_api, synapse.http = mod_api, types.ModuleType("synapse.http")
sys.modules.update({
    "synapse": synapse, "synapse.module_api": mod_api,
    "synapse.http": synapse.http, "synapse.http.server": http_server,
    "synapse.http.site": http_site,
})
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nexlink_invites as m  # noqa: E402


class CodeFormat(unittest.TestCase):
    def test_alphabet_matches_the_client(self):
        # If these ever diverge, the server mints codes the app refuses to
        # accept and the failure looks like a broken invite, not a typo here.
        self.assertEqual(m.ALPHABET, "0123456789ABCDEFGHJKMNPQRSTVWXYZ")
        self.assertEqual(len(m.ALPHABET), 32)
        self.assertFalse(set(m.ALPHABET) & set("ILOU"))

    def test_code_is_twelve_symbols_from_the_alphabet(self):
        for _ in range(200):
            c = m.new_code()
            self.assertEqual(len(c), 12)
            self.assertTrue(set(c) <= set(m.ALPHABET))

    def test_formatting_is_three_groups_of_four(self):
        self.assertEqual(m.format_code("8WGJRYJV4MZ0"), "8WGJ-RYJV-4MZ0")

    def test_codes_do_not_repeat(self):
        self.assertEqual(len({m.new_code() for _ in range(2000)}), 2000)


class FakeTxn:
    def __init__(self, existing=()):
        self.existing = set(existing)
        self.rowcount = 0
        self.sql = None

    def execute(self, sql, args):
        self.sql = sql
        token = args[0]
        self.rowcount = 0 if token in self.existing else 1
        self.existing.add(token)


class Sql(unittest.TestCase):
    def test_a_fresh_code_is_inserted_with_one_use(self):
        txn = FakeTxn()
        self.assertTrue(m._insert_token(txn, "8WGJRYJV4MZ0", 123))
        self.assertIn("uses_allowed", txn.sql)
        self.assertIn("VALUES (?, 1, 0, 0, ?)", txn.sql)

    def test_a_collision_reports_failure_rather_than_raising(self):
        txn = FakeTxn(existing={"8WGJRYJV4MZ0"})
        self.assertFalse(m._insert_token(txn, "8WGJRYJV4MZ0", 123))


class Burst(unittest.TestCase):
    def resource(self, **cfg):
        return m._InviteResource(api=None, config=cfg)

    def test_ordinary_use_is_never_limited(self):
        r = self.resource()
        for _ in range(m.DEFAULT_BURST_PER_HOUR):
            self.assertTrue(r._within_burst("@a:x"))

    def test_a_runaway_loop_is_stopped(self):
        r = self.resource(burst_per_hour=3)
        for _ in range(3):
            self.assertTrue(r._within_burst("@a:x"))
        self.assertFalse(r._within_burst("@a:x"))

    def test_one_user_cannot_exhaust_another(self):
        r = self.resource(burst_per_hour=1)
        self.assertTrue(r._within_burst("@a:x"))
        self.assertFalse(r._within_burst("@a:x"))
        self.assertTrue(r._within_burst("@b:x"))


class Record(unittest.TestCase):
    def test_the_raw_code_is_never_written(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "invites.jsonl")
            r = m._InviteResource(api=None, config={"record_path": path})
            r._record("@luca:x", "8WGJRYJV4MZ0", 999)
            body = open(path).read()
            self.assertNotIn("8WGJRYJV4MZ0", body)
            row = json.loads(body)
            self.assertEqual(row["issuer"], "@luca:x")
            self.assertEqual(len(row["code_sha256"]), 64)
            self.assertEqual(row["expires_at"], 999)

    def test_an_unwritable_record_does_not_stop_an_invite(self):
        r = m._InviteResource(api=None, config={"record_path": "/nonexistent/dir/x.jsonl"})
        r._record("@luca:x", "8WGJRYJV4MZ0", 999)   # must not raise


class DeleteTxn:
    """A txn for DELETE, where the caller decides how many rows matched.
    `FakeTxn` derives rowcount from an insert, which silently overwrote the
    value this test was setting."""

    def __init__(self, rows_affected):
        self.rowcount = rows_affected
        self.sql = None

    def execute(self, sql, args):
        self.sql = sql


class Revoke(unittest.TestCase):
    def test_only_an_unused_code_is_deleted(self):
        txn = DeleteTxn(rows_affected=1)
        self.assertTrue(m._delete_unused_token(txn, "8WGJRYJV4MZ0"))
        # completed = 0 is the guard: a spent token is the server's record that
        # an account was created from it, so deleting it would erase the link.
        self.assertIn("completed = 0", txn.sql)

    def test_a_spent_code_is_left_alone(self):
        txn = DeleteTxn(rows_affected=0)
        self.assertFalse(m._delete_unused_token(txn, "8WGJRYJV4MZ0"))


class Ownership(unittest.TestCase):
    """One user must not be able to see or revoke another's invites — with no
    quota in force this separation is the only thing between 'list your own'
    and 'an authenticated user can shut down everyone's'."""

    def resource(self, path):
        return m._InviteResource(api=None, config={"record_path": path})

    def test_only_the_issuers_own_records_are_returned(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "invites.jsonl")
            r = self.resource(path)
            r._record("@luca:x", "AAAAAAAAAAAA", 1)
            r._record("@dad:x", "BBBBBBBBBBBB", 2)
            r._record("@luca:x", "CCCCCCCCCCCC", 3)

            luca = r._mine("@luca:x")
            self.assertEqual(len(luca), 2)
            self.assertNotIn(
                __import__("hashlib").sha256(b"BBBBBBBBBBBB").hexdigest(), luca)
            self.assertEqual(len(r._mine("@dad:x")), 1)
            self.assertEqual(r._mine("@nobody:x"), {})

    def test_a_truncated_line_does_not_hide_the_rest(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "invites.jsonl")
            r = self.resource(path)
            r._record("@luca:x", "AAAAAAAAAAAA", 1)
            with open(path, "a") as f:
                f.write('{"issuer": "@luca:x", "cod\n')     # a half-written line
            r._record("@luca:x", "CCCCCCCCCCCC", 3)
            self.assertEqual(len(r._mine("@luca:x")), 2)

    def test_a_missing_record_file_is_not_an_error(self):
        r = self.resource("/nonexistent/dir/invites.jsonl")
        self.assertEqual(r._mine("@luca:x"), {})


if __name__ == "__main__":
    unittest.main(verbosity=2)
