# Security Checklist and Open Findings

Project rules: `AGENTS.md` section 14 (Test Integrity) and section 15 (Security and Tenant Isolation).

Tenancy model: each `Branch` is a separate company. Cross-branch data exposure is the highest-severity defect class.

Never write secret values into this file. Name the file and property only.

---

## 1. Open findings

| ID | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| SEC-001 | Critical | Credentials committed to git. `GVRP_Entry_API/src/main/resources/application.properties` holds literal values for `spring.datasource.password`, `spring.gvrp.jwt.secret-key`, `spring.mail.password`, `engine.api.key`, `geocoding.goong.api-key`. `GVRP_Engine_API/src/main/resources/application.properties` holds `entry.api-key`. Remote: GitHub. | File present in git index (checked 2026-10-16); values are literals, not `${...}` placeholders. | OPEN |
| SEC-002 | High | Possible cross-branch access to solutions (IDOR). `SolutionController` endpoints `GET /{id}`, `GET /{id}/latest-revision`, `GET /{id}/revision-chain`, `POST /{id}/evaluate`, `POST /{id}/revisions` load the solution by ID only (`SolutionRepository.findWithDetailsById`, `findById`) and do not compare its branch with the caller's branch. No tenant filter, aspect or interceptor was found. | Static inspection of `SolutionController`, `SolutionService`, `RouteCustomizationService`, `SolutionRepository` (2026-10-16). Not reproduced at runtime. | OPEN, NOT_VERIFIED at runtime |
| SEC-003 | Medium | Other controllers not yet audited for the same pattern. | Only `Solution*` and `OrderRepository` were inspected. `OrderRepository` uses `findByIdAndBranchId`, but `updateStatusByIds` is not branch-scoped, so its callers must validate the IDs. | OPEN, audit pending |
| SEC-004 | Low | Engine callback API key is compared with `String.equals` (not constant-time) in `ApiKeyAuthenticationFilter`. The callback path is `permitAll` in `SecurityConfig` but is protected by this filter. | Static inspection. | OPEN |

### Required actions for SEC-001 (user decision)

1. Rotate every listed credential: Gmail app password, JWT secret, engine/entry API key, Goong API key, DB password. Removing the file does not remove values from git history.
2. Replace the literals with environment placeholders, for example `spring.gvrp.jwt.secret-key=${GVRP_JWT_SECRET}`, and keep local values in a gitignored file or environment variables.
3. Optional and destructive: rewrite history with `git filter-repo` and force-push. Rotation is still required either way.
4. Enable GitHub secret scanning and push protection if available for the repository.

### Required actions for SEC-002 and SEC-003

Open a work item and fix through the business-development workflow:

* Add branch-scoped lookups (for example `findWithDetailsByIdAndBranchId`).
* Take the branch from the authenticated principal (`CurrentUserUtil.getCurrentBranchId()`).
* Add tests proving that branch A gets not-found for branch B's solution on every endpoint listed above.
* Audit every controller under `GVRP_Entry_API/.../controller` for the same pattern.

---

## 2. Stage gates

### Gate A: every change (enforced by CI and review)

- [ ] CI green: build, unit tests, gitleaks
- [ ] No existing test modified, disabled or excluded without recorded user approval (AGENTS.md section 14)
- [ ] New endpoints authenticated; no unjustified `permitAll`
- [ ] Branch-owned data scoped to the principal's branch, with a cross-branch test
- [ ] No secrets, tokens or personal data in code, logs or evidence
- [ ] New dependencies named and verified

### Gate B: before any deployment, including staging

- [ ] SEC-001 resolved: credentials rotated and loaded from the environment
- [ ] SEC-002 and SEC-003 fixed, with cross-branch tests for every controller
- [ ] `logging.level.org.springframework.security` and `org.springframework.web` not at `DEBUG`
- [ ] HTTPS everywhere; CORS restricted to known origins
- [ ] Database user for the application has only the privileges it needs; `ddl-auto=update` replaced by migrations
- [ ] Request size limits on bulk upload and optimization inputs; solver time limit verified
- [ ] Rate limiting on login and optimization submission
- [ ] Dependabot alerts reviewed; no known critical vulnerabilities
- [ ] OWASP ASVS Level 1 checklist walked through once

### Gate C: before real users or real customer data

- [ ] Data inventory: which personal data is stored (customer name, phone, address, coordinates, driver data), where, who can read it, retention
- [ ] Legal review against Vietnam's personal data protection rules (Decree 13/2023/ND-CP and the Personal Data Protection Law; confirm current status with a qualified person)
- [ ] Tests and demos use synthetic data only
- [ ] Backup and restore tested; encryption at rest for the database
- [ ] External penetration test, if budget allows

---

## 3. Automated checks

| Check | Where | Notes |
|---|---|---|
| Build and unit tests | `.github/workflows/ci.yml` | Tests needing MySQL or the GraphHopper graph are excluded in CI; see the workflow comments. |
| Secret scanning | `.github/workflows/ci.yml` (gitleaks) | Scans pushed commits. Existing history already contains SEC-001. |
| Dependency updates | `.github/dependabot.yml` | Maven (both APIs), npm (client v2), GitHub Actions. |
| Static security analysis | Not yet configured | Candidate: Semgrep or CodeQL. |
