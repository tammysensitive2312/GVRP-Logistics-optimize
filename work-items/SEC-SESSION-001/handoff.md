# Handoff — SEC-SESSION-001

Version: working tree on `fix/sec-session-001` at the `pass` transition.
User test performed before recording: disable user → same token 401; re-enable → 200.
Known limitation: tokens remain valid up to 24 h for enabled users (no revocation list; out of scope).
