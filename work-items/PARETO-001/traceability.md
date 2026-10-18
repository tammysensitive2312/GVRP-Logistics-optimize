# Traceability — PARETO-001

| Req | Design | Code (planned) | Test (planned) | Status |
|---|---|---|---|---|
| PAR-001 | A1 | `OptimizationService.optimize`, `optimizeMultiObjective(…, clusterAssignment, …)` | E-T9, E-T10, E-T11 | NOT_VERIFIED |
| PAR-002 | A2 | `ParetoFrontierSelector.classify`, `FrontierStatus` | E-T1, E-T2, E-T3, E-T6 | NOT_VERIFIED |
| PAR-003 | A2 | `ParetoFrontierSelector.select` | E-T4, E-T5, E-T6 | NOT_VERIFIED |
| PAR-004 | A3, A4, A5 | `ParetoAlternative`, `OptimizationResult.pareto`, `SolutionConverter` | E-T7, E-T8, E-T11 | NOT_VERIFIED |
| PAR-005 | A6 | `JobRegistry.JobHandle.scenario*`, `OptimizationController.progress` | E-T11, E-T12 | NOT_VERIFIED |
| PAR-006 | B1, B2 | migration SQL, `ParetoCandidate`, `ParetoCandidateRepository`, `EngineCallbackRequest.ParetoData`, `EnginePlanMaterializer`, `OptimizationCallbackService.saveSolution` | N-T1, N-T2, N-T3 | NOT_VERIFIED |
| PAR-007 | B3, B5 | `ParetoController` GET, `ParetoService.getFrontier`, DTOs, `SolutionMapper` | N-T4, N-T5, N-T6, N-T12 | NOT_VERIFIED |
| PAR-008 | B4 | `ParetoController` POST, `ParetoService.apply`, `SolutionType.PARETO_ALTERNATIVE` | N-T7, N-T8, N-T9, N-T11, N-T12 | NOT_VERIFIED |
| PAR-009 | B3, B4 | branch checks in `ParetoService` | N-T4, N-T10 | NOT_VERIFIED |
| PAR-010 | Tests section | no edits to existing tests | full `mvnw.cmd -B clean test` both modules; byte-compare of 37 snapshotted tests | NOT_VERIFIED |

Code/test links are verified at `implement` / `verify`. Follow-up UI: PARETO-002.
