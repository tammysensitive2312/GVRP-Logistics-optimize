# GVRP Literature Review — Green Vehicle Routing (2026-09-05 investigation)

Scope: Green VRP / Pollution-Routing / EVRP / heterogeneous-fleet / time-dependent-green /
last-mile green, with focus on what is transferable to this project
(GraphHopper + jsprit, Vietnam last-mile, vn_n1000/vn_n5000 + vn6183).

Status: literature triangulation record from a real search (websearch + OpenAlex/Crossref/
Semantic Scholar/arXiv verification, access date 2026-09-05). Inspection levels are honest:
FULL_TEXT = substantial excerpt inspected; ABSTRACT_ONLY = abstract + metadata;
METADATA = existence/year/venue verified only. Abstract-only papers do NOT support
detailed claims about experimental design.

Nothing in this file is promoted to `PROJECT_STATE.md`. Gap verdicts are reviewbounded
and dated; re-verify before use in a thesis.

---

## 1. Protocol used (summary)

Questions: established GVRP/PRP/EVRP variants and limits; emission-model accuracy
(CMEM/MEET/linear/calibrated); differentiated heterogeneous-fleet factors; large-scale
(>=500-1000) green routing on real road networks; Vietnam motorcycle-dominated last-mile;
validation standards (baselines, parity, independent evaluator, stochastic protocol).

Strings (12 combos, incl. falsification): GVRP surveys 2020-2025; PRP/Bektas-Demir;
heterogeneous-fleet emission factor 2021-2025; EVRP surveys 2023-2025; large-scale green
1000-customer real-road-network; time-dependent green congestion; CMEM-vs-MEET comparison;
Vietnam/SE-Asia last-mile green; plus falsification strings (large green VRPTW real
network; green VRP 1000 customers; hetero calibrated factors; jsprit stochastic seeds)
and DOI-resolution searches.

Sources: websearch (live+cached) + paper-lookup (OpenAlex primary) + citation-management
(DOI metadata). Included peer-reviewed + strong surveys, 2020-2026 emphasis, seminal
older allowed. Excluded vendor/blog/AI-summary content as gap evidence; grey reports
(ITDP/GEODIS) used for Vietnam context only.

Saturation: surveys converged on the same core set; new strings returned duplicates;
falsification surfaced counterexamples. Reached for surveys + PRP fundamentals; partial
for Vietnam-motorcycle optimisation (few hits = genuine signal).

Limitation: no institutional access; paywalled texts judged from abstracts + metadata +
two excerpts; 3 guessed DOIs 404'd and were replaced; Vietnamese-language sources not
searched (open item).

---

## 2. Corpus (28 items; 8 surveys, 20 primaries/context)

### Surveys (S1-S8)

| ID | Citation | DOI | Level | Note |
|---|---|---|---|---|
| S1 | Asghari & Mirzapour Al-e-Hashem, IJPE 231, 2021 (313 papers; ICEV/AFV/HEV) | 10.1016/j.ijpe.2020.107899 | ABSTRACT_ONLY + METADATA (OpenAlex verified) | Taxonomy; heavy synthetic-benchmark focus; calls for real-world mixed-fleet work (AUTHOR_STATED_FUTURE_WORK) |
| S2 | Moghdani et al., JCP 279, 2021 (309 papers) | 10.1016/j.jclepro.2020.123691 | ABSTRACT_ONLY + METADATA | Uncertainty + real case studies weakly covered (AUTHOR_STATED) |
| S3 | Marrekchi et al., AnnOR 304, 2021 (review-of-reviews) | 10.1007/s10479-021-04046-8 | ABSTRACT_ONLY + METADATA | Confirms fragmentation; second-order limitation evidence |
| S4 | Garside et al., OR Perspect. 12, 2024 (458 papers; LNS/ALNS, NSGA-II dominant) | 10.60692/ge313-7pm73 | ABSTRACT_ONLY | Method landscape; jsprit ruin&recreate is mainstream |
| S5 | Fernandez Gil et al., J. Adv. Transp. 2022 (89 papers; emission treatment + benchmarks) | 10.1155/2022/5714991 | ABSTRACT_ONLY + METADATA | Emission models heterogeneous/ad hoc; benchmarks mostly Solomon/PRP-library adaptations |
| S6 | Li, Zhou & Xu, Sustainability 15, 2023 (bibliometric, 1230 pubs) | 10.3390/su152316149 | ABSTRACT_ONLY + METADATA | Hotspots fuel+EVs; motorcycle/Vietnam absent (RESEARCHER_INFERRED weak signal) |
| S7 | Shahin & Dulebenets, Cleaner Logist. Transp. 2025 (PRP 2011-2024, 75 papers) | 10.1016/j.clet.2025.101082 | ABSTRACT_ONLY + METADATA (existence verified; volume dated Dec 2025, treat provisional) | Bounds PRP fundamentals as heavily studied |
| S8 | Ghorbani et al., Sustainability 12, 2020 (classification) | 10.3390/su12219079 | METADATA | Corroborating classification only |

### Primaries — PRP / emission-model core (P1-P7)

| ID | Citation | DOI | Level | Note |
|---|---|---|---|---|
| P1 | Bektas & Laporte, TR-B 45, 2011 (seminal PRP) | 10.1016/j.trb.2011.02.004 | ABSTRACT_ONLY + METADATA (OpenAlex verified, ~1193 cites) | Load+speed objective; homogeneous fleet |
| P2 | Demir, Bektas & Laporte, TR-D 16, 2011 (emission-model comparison) | 10.1016/j.trd.2011.01.011 | ABSTRACT_ONLY + METADATA | Basis for CMEM choice in PRP line |
| P3 | Demir, Bektas & Laporte, EJOR 223, 2012 (ALNS for PRP, <=200 nodes) | 10.1016/j.ejor.2012.06.044 | ABSTRACT_ONLY + METADATA | ALNS precedent |
| P4 | Demir, Bektas & Laporte, EJOR 232, 2014 (bi-objective fuel vs time) | 10.1016/j.ejor.2013.08.002 | ABSTRACT_ONLY + METADATA | Small time sacrifice -> large fuel saving; trade-off design input |
| P5 | Franceschetti et al., TR-B 56, 2013 (TDPRP, congestion) | 10.1016/j.trb.2013.08.008 | ABSTRACT_ONLY + METADATA | Wait-to-avoid-congestion theory |
| P6 | Demir, Bektas & Laporte, EJOR 237, 2014 (green road-freight review) | 10.1016/j.ejor.2013.12.033 | METADATA | Pre-2014 boundary only |
| P7 | Turkensteen, EJOR 262, 2017 (accuracy of carbon computations) | 10.1016/j.ejor.2017.04.005 | FULL_TEXT (accepted-manuscript excerpt) | Fixed-speed CMEM can be <50% of realistic-cycle fuel; grows with mass/stop-and-go; lab-cycle limitation (AUTHOR_STATED) |

### Primaries — heterogeneous / time-dependent green (P8-P13)

| ID | Citation | DOI | Level | Note |
|---|---|---|---|---|
| P8 | Koc, Bektas, Jabali & Laporte, TR-B 70, 2014 (fleet-size-and-mix PRP) | 10.1016/j.trb.2014.09.008 | FULL_TEXT (PDF excerpt) + METADATA | Hetero PRP exists (parametric DEFRA factors); hetero-over-homo benefit quantified |
| P9 | Xiao & Konak, TRE 88, 2016 (hetero green VRP + time-varying congestion) | 10.1016/j.tre.2016.01.011 | ABSTRACT_ONLY | Existence proof of het+TD combo on synthetic data; no detailed claims drawn |
| P10 | Liu et al., INFORMS J. Comput. 35, 2023 (BCP exact TDGVRPTW <=100) | 10.1287/ijoc.2022.1195 | ABSTRACT_ONLY + METADATA (multi-source verified) | Exact-scale frontier |
| P11 | Liu et al., EJOR 310, 2023 (ALNS TDGVRPTW up to 1000, Homberger-derived) | provisional (RePEc record) | ABSTRACT_ONLY | Killer counterexample for "no large TDGVRP"; derived (not real-graph) instances |
| P12 | E-HFFVRP family 2018 (emission-based hetero fixed fleet; SATS; carbon tax) | SciDirect PII (DOI unresolved) | ABSTRACT_ONLY | Fixed-fleet emission variant exists; existence evidence only |
| P13 | Jafari et al., Energies 18, 2025 (hybrid ICE/EV/H2 CVRPTW, Decathlon case) | 10.3390/en18195147 | ABSTRACT_ONLY | Powertrain-differentiated priority flips; real-case precedent |

### Primaries — EVRP / benchmarks / methods / Vietnam context (P14-P21)

| ID | Citation | DOI | Level | Note |
|---|---|---|---|---|
| P14 | Schneider, Stenger & Goeke, Transp. Sci. 48, 2014 (E-VRPTW) | 10.1287/trsc.2013.0490 | ABSTRACT_ONLY + METADATA (publisher + Semantic Scholar verified) | Foundational EVRP-TW; EVRP heavily studied separately from ICE-PRP |
| P15 | Raeesi & Zografos, TR-B 2019 (multi-objective Steiner PRP, congested urban) | 10.1016/j.trb.2019.02.008 | ABSTRACT_ONLY + METADATA | Closest real-graph green precedent; small scale |
| P16 | Prakash & Akhtar, RAISD 2025 (Solomon + CO2/energy/noise overlay) | proceedings (no DOI) | ABSTRACT_ONLY | Synthetic overlay, not calibrated factors |
| P17 | Queiroga et al., CVRP-XL (1000-10000) | arXiv-family | ABSTRACT_ONLY | Large VRP benchmarking exists (not green); kills generic "no large benchmark" |
| P18 | SVRPBench, arXiv:2505.21887 (stochastic VRP 10-1000) | 10.48550/arXiv.2505.21887 | ABSTRACT_ONLY (verified; NON-GREEN scope caveat) | Stochastic protocol precedent; must NOT be cited as green evidence |
| P19 | jsprit issues/forum (threads #156, #479; Homberger tuning) | none (software grey lit) | ABSTRACT_ONLY (excerpts) | Single-thread+iteration-cutoff ~= reproducible; multi-thread variance 1-2%; supports >=5-seed + budget rule |
| P20 | ITDP Vietnam 2025; Acta Logistica VN 2026; GEODIS 2026 | none (reports) | ABSTRACT_ONLY | Context only (~70% Hanoi LMD motorcycles; 90.83 g/km MC factor cited; pilots; Hanoi ban). NOT optimisation evidence |
| P21 | CIRRELT 2019-08 (CMEM vs MEET vs ML on field data) | working paper | FULL_TEXT (results excerpt) | MEET -24.9%, CMEM +13.2% (calibrated +7.6%), GBM -1.7%; classical models biased |

Reviewer re-verified (independent): Asghari, Bektas&Laporte, Turkensteen (via RePEc/ScienceDirect/Semantic Scholar),
Koc, Schneider (publisher + Semantic Scholar), Liu BCP (multi-source), Raeesi, SVRPBench (with non-green caveat),
Shahin&Dulebenets (partially; future-dated volume). Reviewer falsification searches: F1 hetero calibrated factors
(surfaced Wang 2025 LRP, Li 2018 E-HFFVRP — narrow, don't kill VN framing); F2 e-motorcycle VN routing (no OR paper
found — strengthens GAP-3); F3 Pareto weight sensitivity (PLOS Complex Syst. 2026 bi-objective + carbon-policy
sensitivity; Tehran MOSVRP 2025 sensitivity tables — substantially narrow GAP-5); F4 real-network PRP (Lai 2024
PRP-SO + topography; London/Berlin/Portland cases — narrow GAP-2's real-network leg).

---

## 3. Landscape (refined from corpus)

Problem: PRP -> bi-objective PRP -> TDPRP -> fleet-size-mix PRP -> E-HFFVRP -> EVRP/E-VRPTW
(separate line) -> Steiner-PRP on road graph -> TDGVRPTW exact (<=100) / heuristic (<=1000 derived).
Env-model: linear distance/load; average-speed CMEM (dominant); instantaneous CMEM (data-hungry);
MEET polynomial; agency factors; ML-learned (best accuracy, rarely in routing loop).
Objective: single vs bi/multi (fuel vs time; cost vs emissions); weight choices usually ad hoc.
Network: Euclidean/Solomon-derived (dominant) -> PRP-library UK distances (small) -> urban road
graph/Steiner (rare, small) -> real traffic-speed profiles (rare). Scale: exact <=100;
metaheuristic 100-200 typical; 500-1000 demonstrated but almost never green + real-graph +
heterogeneous jointly. Method: exact small; ALNS/LNS dominant single-objective; NSGA-II
dominant multi-objective; VNS/TS/tabu/ACO; ML/DRL emerging. Validation: Solomon/GH
hierarchical standard; emission overlays; stochastic repetitions inconsistent; service parity
rarely explicit; optimisation-model vs evaluation-model separation almost never done.

Heavily studied (do NOT propose): PRP load+speed formulation; CMEM-average-speed; ALNS/LNS;
NSGA-II; EVRP-TW/charging; TDPRP theory; hetero-fleet PRP existence; Solomon benchmarking.

---

## 4. Gap verdicts (reviewed 2026-09-05; scope-bound, dated)

| Gap | Statement (narrow) | Verdict | Strength | Condition |
|---|---|---|---|---|
| GAP-1 | VN-fleet-calibrated, vehicle-differentiated emission factors (project uses 16 identical factors — premise NOT source-traced) | PLAUSIBLE_GAP (applied; NOT a fundamental field gap) | MODERATE | Scope to VN calibration; verify 16-factor premise; fix before GAP-5 |
| GAP-2 | Green routing at 1000-5000 scale on real GraphHopper matrices + hetero fleet (conjunction only) | PLAUSIBLE_GAP (conjunction; WEAK if broader) | LIMITED-MODERATE | State with search date; parity + stochastic discipline required; don't cite SVRPBench as green |
| GAP-3 (narrowed 2026-09-06) | No peer-reviewed study LOCATED as of 2026-09-06 jointly satisfying (a) motorcycle-specific energy/emission model, (b) explicit emission term in the routing objective (not ex-post distance×factor estimation), (c) Vietnam instances. Nearest neighbours: Lee et al. 2020, Sustainability, doi:10.3390/su12031077 (VN-framed moto-inclusive VRP-EL, cost/distance objective, ex-post carbon estimation); Dao & Cho 2025, JKMS, doi:10.9717/kmms.2025.28.1.046 (moto-specific MDVRPTW HCMC, abstract-level, no green content in abstract/keywords, full text unchecked) | NARROWED (was SUPPORTED_GAP) | MODERATE (conjunction only; broader readings LIMITED) | VN-language/repository pass still open; KCI-indexing of N2 unverified; N1 instance/scale details not re-verified; do not reuse truck CMEM for motos |
| GAP-4 | Independent evaluator + service parity + stochastic protocol | SUPPORTED as internal protocol requirement; WEAK_GAP as literature gap (category error) | STRONG (prescription) / NONE (novelty) | Reclassify as protocol amendment, not a gap |
| GAP-5 | Carbon-vs-cost weight/Pareto sensitivity under differentiated fleet | WEAK_GAP as field gap (near-contradicted broadly); PLAUSIBLE as project experiment | MODERATE (narrowing evidence) | Gate on GAP-1 + GAP-4; pre-register grid |

Rejected-as-stated (endorsed with caveats): "no large green routing" (killed by Liu ALNS-1000 + XL-CVRP);
"hetero fleet unstudied" (killed by Koc/E-HFFVRP/hybrid-fleet); "no TD green" (Franceschetti/Liu family);
"no emission-model comparison" (Demir2011/Turkensteen/CIRRELT); universal "carbon-aware always wins"
(rejected; conditional hypothesis only).

Open items: source-trace 16-factor + UNASSIGNED premises; VN-language search pass (GAP-3 falsification
pass 2026-09-06 ran Vietnamese queries but login-walled thesis repositories, VJOL, RAISD-beyond-P16,
citation-chasing N1/N2, and the IEOM-Rome-2021 HCMC green-VRP item remain untriaged); truck->moto energy-model
boundary; CMEM parameter reconciliation across papers; 2025-2026 items still evolving.
