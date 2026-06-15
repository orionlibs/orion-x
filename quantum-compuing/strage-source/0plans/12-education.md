# Section 12 — Education & Documentation: Implementation Plan

> Scope: Jupyter/JShell notebook examples for every algorithm in `algorithm/`; an
> interactive tutorial set (superposition → entanglement → teleportation →
> Deutsch-Jozsa → Grover → Shor → VQE); worked complexity analysis per algorithm;
> and a quantum-terms glossary embedded in Javadoc.
>
> Note on the brief: the overall idea file referenced as `1.md` lives in this repo
> as `ideas.md`. Section 12 of `ideas.md` (lines 223-228) is the source for this plan.

---

## 1. Overview & Goals

Strange is a pure-Java quantum library (`org.redfx.strange`). Today its only
"documentation by example" is `demo/Demo.java` (a `main` that runs hard-coded
arithmetic experiments and dumps to `System.err`) and the algorithm Javadoc in
`algorithm/Classic.java`. There is no onboarding path, no runnable tutorials, no
conceptual glossary, and no complexity discussion. A newcomer cannot go from
"I cloned the repo" to "I ran a superposition circuit" without reading source.

Goals:

1. **Runnable, CI-verified examples** for each `Classic` algorithm using the
   *real* API (constructors and method names verified against
   `Program.java`, `Step.java`, `Qubit.java`, `Result.java`, `Classic.java`,
   the `gate/` package).
2. **A guided tutorial progression** from one-qubit superposition up to VQE, with
   each tutorial available in two formats: a `.jsh` JShell script (zero external
   tooling, runs anywhere a JDK is) and an `.ipynb` Jupyter notebook using the
   **IJava kernel** (Strange is Java, so the kernel must be a Java kernel, not
   Python).
3. **Worked complexity analysis** colocated with each algorithm so the asymptotics
   are discoverable from the code.
4. **A quantum glossary** embedded in Javadoc via `package-info.java` files plus a
   shared `doc-files/glossary.html`, cross-linked with `{@link}` / `@see`.

Non-goals: this section writes *docs and examples only*. Tutorials that require
new library code (teleportation, VQE, Deutsch-Jozsa) are written against the
**planned** APIs of other sections and are explicitly gated on them (see §7).
Where the algorithm does not yet exist, the tutorial ships as a stub that fails
CI loudly until the dependency lands, OR (preferred for early delivery) is
hand-built from primitives that already exist.

---

## 2. Current Examples / Demo Analysis

### `algorithm/Classic.java` — the algorithms to document

Verified entry points and their real signatures:

| Method | Signature | What it builds |
|---|---|---|
| `randomBit()` | `static int randomBit()` | 1-qubit `Program`, single `Step(new Hadamard(0))`, measures `qubits[0]`. The minimal superposition example. |
| `qsum(a, b)` | `static int qsum(int a, int b)` | QFT adder: `X` prep gates, `Fourier(m,0)`, a triangle of `Cr(i, cr0, 2, 1+j)` controlled rotations, `InvFourier(m,0)`. |
| `search(list, fn)` | `static <T> T search(List<T>, Function<T,Integer>)` | Grover. Delegates to `searchProbabilities`, returns argmax element. |
| `searchProbabilities(list, fn)` | `static <T> double[] searchProbabilities(...)` | Grover loop: `Hadamard` on all `n` wires, then `~π√N/4` iterations of `Oracle` + diffusion `Oracle` + `ProbabilitiesGate(0)`. Returns `abssqr` of `res.getProbability()`. |
| `findPeriod(a, mod)` | `static int findPeriod(int a, int mod)` | Shor period-finding; calls private `measurePeriod`, then `Computations.fraction`. |
| `qfactor(N)` | `static int qfactor(int N)` | Full Shor: random `a`, `Computations.gcd`, `findPeriod`, classical post-processing. |

Key API facts the examples must respect (verified, not assumed):

- `new Program(int nQubits, QuantumStep... moreSteps)` — qubits default to `|0⟩`.
- `new QuantumStep(Gate... gates)` and `new QuantumStep(String name, Gate... gates)`;
  `step.addGate(Gate)` / `step.addGates(Gate...)`.
- `program.addStep(Step)` / `program.addSteps(Step...)`.
- Execute via `QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();`
  then `Result res = qee.runProgram(program);` (note: `runProgram`, **not** `execute`).
- `res.getQubits()` → `Qubit[]`; `qubit.measure()` → `int` (0/1);
  `qubit.getProbability()` → `double`.
- `res.getProbability()` → `Complex[]` (full state-vector amplitudes);
  `complex.abssqr()` → `double` probability.
- `res.getIntermediateProbability(int QuantumStep)` and `res.getIntermediateQubits()`
  expose per-step state — useful for Grover-iteration visualisation tutorials.
- `Classic.setQuantumExecutionEnvironment(qee)` swaps the backend (the algorithms
  use a static `qee` field) — tutorials should mention this for backend swapping.
- Gates available for tutorials: `Hadamard`, `X`, `Y`, `Z`, `Cnot`, `Cz`, `Cr`,
  `Swap`, `Toffoli`, `Fourier`/`InvFourier`, `Oracle`, `ProbabilitiesGate`,
  `RotationX/Y/Z`, `Measurement`, `ProbabilitiesGate` (all in `gate/`).
- `Complex.tensor`, `Complex.mmul`, `Complex.ONE`, `Complex.ZERO` for matrix-level
  oracle construction (as `createGroverOracle` / `createDiffMatrix` do).

### `demo/Demo.java` — existing style to mirror and improve on

`Demo.main` calls `expmul2p3mod7gen()` and prints measurements with
`System.err.println`. Patterns to reuse: build `Program`, add prep `Step`s with
`X`/`Hadamard`, run via `SimpleQuantumExecutionEnvironment`, loop
`q[i].measure()`. Patterns to **avoid** in tutorials: dead commented code,
`System.err` for results, no assertions, magic indices without explanation. The
tutorials should narrate the index arithmetic that `Demo` leaves implicit.

---

## 3. Detailed Work Items

### 3.1 Directory layout

The repo is flat (sources at root). Keep docs/examples out of the source tree so
the Maven/Javadoc build is unaffected, except for `package-info.java` files which
*must* live beside the sources to attach to packages.

```
docs/
  README.md                      # docs landing page: how to run jshell + notebooks
  getting-started.md             # 5-minute "first circuit" using randomBit
  complexity/
    randomBit.md
    qsum.md
    grover.md
    shor.md
    deutsch-jozsa.md             # ships with the DJ algorithm (Section 3 dep)
    vqe.md                       # ships with VQE (Section 7 dep)
  glossary.md                    # human-readable master glossary (source of truth)
examples/
  jshell/
    00-quickstart.jsh
    01-superposition.jsh
    02-entanglement-bell.jsh
    03-teleportation.jsh
    04-deutsch-jozsa.jsh
    05-grover.jsh
    06-shor.jsh
    07-vqe.jsh
    run-all.sh                   # CI driver: runs every .jsh, asserts exit 0
    strange-classpath.env        # resolves built jar / target/classes for --class-path
  notebooks/
    00-quickstart.ipynb
    01-superposition.ipynb
    02-entanglement-bell.ipynb
    03-teleportation.ipynb
    04-deutsch-jozsa.ipynb
    05-grover.ipynb
    06-shor.ipynb
    07-vqe.ipynb
    README.md                    # IJava kernel install + run instructions
# beside sources (attach Javadoc to packages):
package-info.java                                 # org.redfx.strange
gate/package-info.java                            # org.redfx.strange.gate
algorithm/package-info.java                       # org.redfx.strange.algorithm
local/package-info.java                           # org.redfx.strange.local
doc-files/glossary.html                           # rendered glossary for Javadoc {@link}
```

Rationale: `.jsh` is the lowest-friction format (no kernel, no pip, runs in CI
with `jshell --class-path …`); notebooks are the richer teaching artefact but
depend on IJava being installed. Shipping both means CI can always verify the
`.jsh` path even if notebook execution is optional.

### 3.2 JShell quickstart — `examples/jshell/00-quickstart.jsh`

Purpose: prove the install works and show the canonical run loop. Content:

```java
// Run with:  jshell --class-path target/classes 00-quickstart.jsh
import org.redfx.strange.*;
import *;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;

Program program = new Program(1, new QuantumStep(new Hadamard(0)));
QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();
Result result = qee.runProgram(program);
Qubit q0 = result.getQubits()[0];
System.out.println("P(|1>) = " + q0.getProbability());   // ~0.5
System.out.println("measured = " + q0.measure());        // 0 or 1
/exit
```

The classpath helper (`strange-classpath.env`) records where the compiled classes
are (`target/classes` after `mvn compile`, or the built jar) so every script and
the CI driver share one source of truth.

### 3.3 Per-tutorial notebooks + JShell (API sketches)

Each tutorial exists as both `.jsh` and `.ipynb` with identical Strange calls;
the notebook adds Markdown narration, the LaTeX state math, and (where useful) a
probability bar chart. Each ends with a **self-check assertion** so CI can detect
silent breakage.

#### Tutorial 01 — Superposition (`01-superposition`)
Concept: a single Hadamard creates an equal superposition; measurement is
probabilistic. Built directly from `Classic.randomBit`'s circuit.
```java
Program p = new Program(1, new QuantumStep(new Hadamard(0)));
Result r = new SimpleQuantumExecutionEnvironment().runProgram(p);
double pOne = r.getQubits()[0].getProbability();          // expect ~0.5
assert Math.abs(pOne - 0.5) < 1e-9 : "not in superposition";
// also: call Classic.randomBit() 1000x, show ~50/50 histogram
```
Dependencies: none (existing API).

#### Tutorial 02 — Entanglement / Bell state (`02-entanglement-bell`)
Concept: H then CNOT produces |Φ+⟩ = (|00⟩+|11⟩)/√2; the two qubits' outcomes are
perfectly correlated. Uses `Hadamard` + `Cnot` (both exist).
```java
Program p = new Program(2);
p.addStep(new QuantumStep(new Hadamard(0)));
p.addStep(new QuantumStep(new Cnot(0, 1)));
Result r = new SimpleQuantumExecutionEnvironment().runProgram(p);
Complex[] amp = r.getProbability();        // length 4: [|00>,|01>,|10>,|11>]
// assert amp[0].abssqr()≈0.5, amp[3].abssqr()≈0.5, amp[1]≈amp[2]≈0
r.measureSystem();                          // measures whole system consistently
Qubit[] q = r.getQubits();
assert q[0].getMeasuredValue() == q[1].getMeasuredValue();   // correlated
```
Notebook adds: amplitude table, explanation of why `getProbability()` is the full
2^n state vector vs `getQubits()[i].getProbability()` being marginal per-qubit.
Dependencies: none.

#### Tutorial 03 — Teleportation (`03-teleportation`)
Concept: transmit an unknown qubit state using a shared Bell pair + 2 classical
bits. Requires mid-circuit measurement and classically-conditioned corrections.
```java
// 3 qubits: q0 = message, (q1,q2) = Bell pair
Program p = new Program(3);
p.initializeQubit(0, alpha);                 // prepare message state on q0
p.addStep(new QuantumStep(new Hadamard(1)));
p.addStep(new QuantumStep(new Cnot(1, 2)));         // entangle q1,q2
p.addStep(new QuantumStep(new Cnot(0, 1)));
p.addStep(new QuantumStep(new Hadamard(0)));
// measure q0,q1 -> classical bits, then conditionally apply X/Z on q2
```
**Dependency (Section 7 — dynamic circuits / classical control):** Strange today
lacks `ClassicalRegister` + `If(creg==v, gate)`. Two delivery options:
  (a) **Early/standalone version** using `ImmediateMeasurement` (exists in `gate/`)
      and rebuilding the post-measurement corrections as separate
      `Program` runs driven by the measured classical bits in Java — accurate to
      current API, ships now.
  (b) **Final version** once Section 7's dynamic-circuit `If` gate exists,
      expressed as a single `Program`.
The tutorial ships version (a) and is annotated to upgrade to (b).

#### Tutorial 04 — Deutsch-Jozsa (`04-deutsch-jozsa`)
Concept: one oracle query decides constant vs balanced. Natural teaching bridge to
Grover. **Dependency (Section 3):** `DeutschJozsa` is listed as missing in
`ideas.md` §3. Until it lands, the tutorial hand-builds the circuit with the
existing `Oracle` gate (constructed from a permutation/phase matrix, exactly as
`Classic.createGroverOracle` builds an `Oracle` from a `Complex[][]`):
```java
int n = 2;                                   // 2 input qubits + 1 ancilla
Program p = new Program(n + 1);
p.addStep(new QuantumStep(new X(n)));               // ancilla -> |1>
Step h = new QuantumStep();
for (int i=0;i<=n;i++) h.addGate(new Hadamard(i));
p.addStep(h);
p.addStep(new QuantumStep(new Oracle(balancedOrConstantMatrix)));  // Uf
Step h2 = new QuantumStep();
for (int i=0;i<n;i++) h2.addGate(new Hadamard(i));
p.addStep(h2);
// measure inputs: all-zero => constant, else balanced
```
Notebook shows the `Complex[][]` oracle construction so the "oracle" concept is
concrete. Upgrades to call `algorithm.DeutschJozsa` once Section 3 delivers it.

#### Tutorial 05 — Grover (`05-grover`)
Concept: amplitude amplification finds a marked item in ~`π√N/4` queries. Uses the
**existing** `Classic.search` / `Classic.searchProbabilities`.
```java
List<Integer> data = List.of(0,1,2,3,4,5,6,7);
Integer found = Classic.search(data, x -> x == 5 ? 1 : 0);   // returns 5
double[] probs = Classic.searchProbabilities(data, x -> x == 5 ? 1 : 0);
// assert argmax(probs) corresponds to 5
```
Notebook deep-dive: reconstruct the loop from `searchProbabilities` (Hadamard
layer, `Oracle("O")`, diffusion `Oracle("D")`, `ProbabilitiesGate(0)` per
iteration) and plot probability vs iteration using
`res.getIntermediateProbability(step)` to show amplification building up.
Dependencies: none (uses existing algorithm); intermediate-state plotting uses
existing `Result` API.

#### Tutorial 06 — Shor (`06-shor`)
Concept: factor `N` via quantum period-finding. Uses existing
`Classic.findPeriod` / `Classic.qfactor`.
```java
int period = Classic.findPeriod(7, 15);      // period of 7^x mod 15
int factor = Classic.qfactor(15);            // -> 3 or 5
assert 15 % factor == 0 && factor != 1 && factor != 15;
```
Notebook narrates the pipeline: `gcd(a,N)` shortcut, the `measurePeriod`
register layout (`2*length+2+offset` qubits, Hadamard prep, `MulModulus` inside
`ControlledBlockGate`, `InvFourier`), continued-fraction recovery
(`Computations.fraction`), and the classical even-period / `a^(p/2)±1` QuantumStep.
Because `qfactor` is randomised (`Math.random()` pick of `a`) and may recurse,
the CI assertion must use a fixed small `N` and only assert "found a nontrivial
factor", not a specific value.
Dependencies: none.

#### Tutorial 07 — VQE (`07-vqe`)
Concept: variational ground-state estimation; classical optimizer drives quantum
expectation values. **Dependency (Section 7 — hybrid execution + Section 1/3):**
needs `ParametricProgram` / parametric gates, an `Estimator` (`⟨ψ|H|ψ⟩`), and a
classical optimizer — none exist yet. Delivery:
  (a) **Pedagogical pre-version** using existing `RotationX/Y/Z` gates as a fixed
      ansatz, computing expectation by hand from `res.getProbability()` for a
      simple Z-basis Hamiltonian, and doing a manual parameter sweep in Java to
      show the energy-vs-angle curve. Ships now, accurate to current API.
  (b) **Full version** once Section 7's `Estimator`/`Sampler`/optimizer land.
The notebook ships (a) and is the last in the progression because it assumes
every prior concept (superposition, rotation, expectation, measurement).
Dependencies: Section 7 (full), Section 1 parametric-gate unification (nice-to-have).

### 3.4 Complexity-analysis docs (`docs/complexity/*.md`)

One file per algorithm, each linked from the corresponding algorithm Javadoc via
`{@link}`/`@see` and from the notebook. Required content per file:

- **`randomBit.md`** — 1 qubit, 1 gate, O(1) gates; simulator cost O(2^1). The
  point: trivial circuit, used to anchor the "gate count vs simulation cost"
  distinction (gate count is circuit complexity; the dense simulator is the
  O(2^n) factor everywhere below).
- **`qsum.md`** — QFT adder on `m+n` qubits. QFT is O(m^2) controlled rotations
  (the `Cr` triangle), addition is O(m^2) gate depth; classical adder is O(m).
  Show that the quantum advantage here is *not* speed but reversibility /
  in-place-on-amplitudes behaviour. Simulator cost O(2^(m+n)).
- **`grover.md`** — `n=⌈log₂ size⌉`, `N=2^n`. Query complexity
  Θ(√N) (the `π√N/4` iteration count from `searchProbabilities`) vs classical
  Θ(N). Each iteration: oracle + diffusion, each O(poly(n)) gates but the dense
  simulator applies an O(N×N) `Oracle` matrix, so *simulated* cost is O(N²) per
  iteration → O(N^2.5) total. Distinguish query vs gate vs simulation cost
  explicitly.
- **`shor.md`** — factoring `N`, `L=⌈log₂N⌉` bits. Quantum part: O(L²) to O(L³)
  gates dominated by modular exponentiation (`MulModulus` in
  `ControlledBlockGate`) and the inverse QFT; vs best classical (GNFS)
  sub-exponential `exp(O((log N)^{1/3}(log log N)^{2/3}))`. Note the register size
  in `measurePeriod` (`2*length+2+offset`) and why the dense simulator caps this
  at very small `N`.
- **`deutsch-jozsa.md`** (with DJ) — 1 quantum query vs 2^{n-1}+1 classical
  worst-case queries; exact, deterministic separation.
- **`vqe.md`** (with VQE) — per-iteration circuit cost × number of optimizer
  iterations × shots; no proven asymptotic speedup, heuristic. Emphasise the
  classical-loop cost.

Each file follows a fixed template: *Problem → Classical baseline → Quantum gate
complexity → Query complexity (if applicable) → Simulation cost in Strange's dense
simulator → Caveats*.

### 3.5 Javadoc glossary

Approach: master glossary authored once in `docs/glossary.md` (human source of
truth), rendered to `doc-files/glossary.html` for Javadoc inclusion. Terms to
cover (minimum): qubit, superposition, amplitude, probability vs amplitude,
measurement / collapse, basis state, entanglement, Bell state, tensor product,
unitary, Hadamard, Pauli (X/Y/Z), phase, CNOT/controlled gate, oracle, ancilla,
diffusion operator, QFT, period-finding, continued fractions, controlled-block
gate, expectation value, ansatz, variational, decoherence/noise.

Wiring into Javadoc:
- Add `package-info.java` to each package with an overview paragraph and a
  prominent `@see <a href="../doc-files/glossary.html">Quantum glossary</a>`
  (relative path resolves under the generated Javadoc tree).
- In `algorithm/package-info.java`, link each algorithm to its
  `docs/complexity/*.md` analysis and to the matching glossary anchors
  (e.g. `glossary.html#grover-diffusion`).
- In key class/method Javadoc (proposed *additions*, not part of this
  doc-only deliverable but specified here for Section follow-up): add `@see`
  glossary anchors — e.g. on `Hadamard` → `#superposition`, on
  `Classic.search` → `#grover-diffusion`, `#oracle`. This plan **does not edit
  source**; it specifies the `@see` tags for a later code change so the glossary
  links are bidirectional.
- The glossary HTML uses `<dl>` with anchored `<dt id="…">` so `{@link}`/`@see`
  href fragments are stable.

### 3.6 README / getting-started

- `docs/getting-started.md`: clone → `mvn compile` → run
  `examples/jshell/00-quickstart.jsh` → expected output → next QuantumStep pointers to
  each tutorial in progression order.
- `examples/notebooks/README.md`: install JDK 11+, install the **IJava** kernel
  (`python -m jupyter` is only the launcher; the kernel itself is Java), point
  IJava at the Strange jar via `%jars`/`%classpath` magics, then "Run All".
- Top-level `docs/README.md`: index of tutorials, complexity docs, glossary.

---

## 4. Cross-Section Dependencies

| Tutorial / doc | Needs from | Status now | Mitigation |
|---|---|---|---|
| 03 Teleportation | §7 dynamic circuits (`ClassicalRegister`, `If`) | missing | Ship version (a) using `ImmediateMeasurement` + Java-driven corrections |
| 04 Deutsch-Jozsa | §3 `DeutschJozsa` algorithm | missing | Hand-build with existing `Oracle`; upgrade later |
| 07 VQE | §7 `Estimator`/`Sampler`/optimizer; §1 parametric gates | missing | Ship version (a) with `RotationX/Y/Z` ansatz + manual sweep |
| Grover intermediate-state plot | none (uses `getIntermediateProbability`) | available | — |
| Backend-swap mention | §6 hardware backends | stub only | Document `setQuantumExecutionEnvironment` indirection only |
| Complexity docs for DJ/VQE | same as their tutorials | gated | Ship with the algorithm |

Everything for Tutorials 01, 02, 05, 06 and their complexity docs is buildable
**today** against the existing API and should land first.

---

## 5. Testing / CI Strategy

1. **JShell scripts must run with exit 0.** `examples/jshell/run-all.sh` does
   `mvn -q compile` then, for each `*.jsh`, runs
   `jshell --class-path "$STRANGE_CP" "$f"` and fails the job on any non-zero
   exit or on any line printing `Exception`. Assertions inside scripts use
   `assert` with `jshell -R-ea` (enable assertions) so self-checks actually throw.
2. **Notebooks must execute without error.** A CI job installs the IJava kernel and
   runs `jupyter nbconvert --to notebook --execute --ExecutePreprocessor.timeout=600`
   on every `.ipynb`, failing on any cell error. Gate this job behind a label or
   make it `continue-on-error` initially since IJava install is heavier; the
   `.jsh` job is the hard gate.
3. **Determinism guards.** Probabilistic algorithms (`randomBit`, Grover argmax,
   `qfactor`'s random `a`) must assert on *probabilities/invariants*, never exact
   measured bits. Shor asserts "nontrivial factor of fixed small N"; Grover
   asserts argmax index; superposition asserts P≈0.5 within tolerance.
4. **Size limits.** The dense simulator is O(2^n); keep every example ≤ ~10 qubits
   so CI stays fast (Shor uses N=15 at most). Document this cap in each notebook.
5. **Doc link check.** A lightweight job greps generated Javadoc for broken
   `glossary.html#…` fragments and verifies every `docs/complexity/*.md` is linked
   from a `package-info.java`.
6. **Gated tutorials** (03/04/07 final versions) carry a CI tag that is skipped
   until the dependent section ships; their version-(a) forms run in the normal
   gate.

---

## 6. Risks

- **IJava kernel friction.** IJava install/classpath is the most likely CI
  flake. Mitigation: `.jsh` is the primary verified artefact; notebooks are
  secondary.
- **Randomised algorithms breaking CI.** `qfactor` recurses on bad `a` and uses
  `Math.random()`; `randomBit`/Grover are probabilistic. Mitigation: assert
  invariants, bound `maxtries`, fix inputs, allow Shor a retry budget.
- **API drift.** If other sections rename `runProgram`/`getProbability`, every
  example breaks. Mitigation: examples are CI-gated, so drift is caught
  immediately; centralise imports/classpath in one env file.
- **Teaching against not-yet-built APIs (03/04/07).** Risk of documenting a future
  API that changes. Mitigation: ship version-(a) forms that only use today's API;
  keep the future-API form as a clearly-labelled appendix.
- **Simulator scale.** Naive notebook examples could request 20+ qubits and OOM.
  Mitigation: explicit qubit caps + a note linking to §2 (sparse/MPS) of
  `ideas.md`.
- **Glossary/Javadoc maintenance drift.** Two copies (md + html). Mitigation:
  generate `doc-files/glossary.html` from `docs/glossary.md` in the build, or
  document the regeneration QuantumStep.

---

## 7. Suggested Sequencing

1. **Foundations (no deps):** directory scaffold, `strange-classpath.env`,
   `00-quickstart.jsh` + notebook, `getting-started.md`, `run-all.sh`, and the
   `.jsh`-runs-in-CI gate. Proves the harness.
2. **Existing-API tutorials:** 01 Superposition → 02 Entanglement → 05 Grover →
   06 Shor (both `.jsh` and `.ipynb`), with their `docs/complexity/*.md`.
3. **Glossary + Javadoc wiring:** `docs/glossary.md`, `doc-files/glossary.html`,
   `package-info.java` files, the link-check CI job, and the *specification* of
   `@see` tags to add to source later.
4. **Notebook execution CI job** (IJava) as a secondary gate.
5. **Gated tutorials, version (a):** 03 Teleportation (via `ImmediateMeasurement`),
   04 Deutsch-Jozsa (via `Oracle`), 07 VQE (via `RotationX/Y/Z` + manual sweep),
   plus their complexity docs.
6. **Upgrade pass (after Sections 3/7 land):** swap 03/04/07 to the real
   `algorithm.*` APIs; remove the appendix stubs; enable their full-version CI
   tags.

This ordering delivers a complete, CI-verified learning path using only today's
API by QuantumStep 3, and lights up the dependent tutorials as the other sections land.
