# Security

Never post API keys, tokens, authorization codes, callback URLs, cookies, PKCE
material, signing keys or credential-bearing reproduction data in public issues.
Redact logs and use synthetic credentials. If credentials were exposed, rotate or
revoke them through the provider; deleting a Git file does not remove its history.

For authentication, storage or credential-transfer vulnerabilities, prefer private
reporting to the repository maintainers when available. GitHub Private Vulnerability
Reporting was not verified as available/enabled during the publication audit
(the private repository endpoint returned 404). Configure it before public launch.
No private reporting email is designated. If no confidential channel is available,
request one without disclosing exploit details or credentials publicly.

Review [PROJECT_SPEC.md](PROJECT_SPEC.md) for storage/protocol requirements and
[PRIVACY.md](PRIVACY.md) for data flow. There is no stable supported release or
promised security-response SLA at this development stage.
