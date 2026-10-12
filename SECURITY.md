# Security Policy

## Reporting a vulnerability

Please report security issues privately rather than in a public issue. Use
GitHub's private vulnerability reporting (**Security → Report a vulnerability**)
on this repository, or email the maintainers at **security@dogsbay.ai**.
<!-- MAINTAINER: confirm this disclosure address before the public release. -->

We aim to acknowledge reports within a few working days and will keep you
updated as we investigate and fix.

## Supported versions

DogsBay XML is pre-1.0 (current line: 4.0.0-beta). Security fixes target the
latest release; there is no backport guarantee for older betas.

## Security posture

A few things worth knowing when assessing risk:

- **Local integration server is off by default.** The embedded HTTP server that
  exposes MCP, JSON-RPC, and REST (for CLI editor-commands and AI assistants)
  does not run unless you enable it in Preferences → Server. When enabled it:
  - binds to the loopback interface only (`localhost`);
  - requires a bearer token on every request (256-bit `SecureRandom`, persisted
    at `~/.dogsbay/auth_token` with owner-only permissions);
  - compares the token in constant time and rejects requests with a non-loopback
    `Host` or a cross-origin `Origin` (DNS-rebinding defense).
- **XML parsing is hardened against XXE/SSRF.** Parser and transformer factories
  used for opening, validating, and transforming documents disable external
  general/parameter entities and external DTD/stylesheet access, while still
  resolving DITA grammars through the bundled XML catalog. See
  `com.dogsbay.xml.SecureXml` and `com.dogsbay.dogsbayaieditor.links.HardenedSax`.
- **Startup update check.** By default the editor makes one outbound HTTPS
  request to the GitHub Releases API on startup to check for a newer version.
  It sends no user data beyond what any HTTPS request reveals and can be turned
  off (`update-check-enabled` preference).

## Known areas under active hardening

- The integration server does not yet confine filesystem access to a workspace
  root — a client holding the token can read/write files the process user owns.
  Only enable the server for trusted local clients. An optional workspace-root
  confinement is planned.
