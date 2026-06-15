# Section 7 — Classical–Quantum Hybrid Execution

Implementation plan for variational support (`ParametricProgram`), the `Estimator` /
`Sampler` APIs, classical optimizers (SPSA, gradient descent, Apache Commons Math
adapter), and dynamic circuits (`ClassicalRegister`, conditional gate application) for
the Strange library (`org.redfx.strange`).

All file paths below are relative to the repo root
(`/Users/dimiefthymiou/workspaces/misc/orion-x/quantum-compuing/strage-source`).
Source files referenced are at the repo root (flat layout: core classes at root,
gates under `gate/`, simulator under `local/`, algorithms under `algorithm/`).

---

## 1. Overview & Goals

Add a *hybrid execution* layer on top of the existing pure state-vector simulator so
that variational algorithms (VQE, QAOA — Section 3) become first-class:

1. **`ParametricProgram` + `Parameter`** — a circuit template with named, free angle
   parameters. Binding a map `name → value` produces a concrete, runnable `Program`.
2. **`Estimator`** — computes `⟨ψ|H|ψ⟩` for a Pauli `Hamiltonian` *exactly from the
   simulated state vector*, without sampling and without collapsing the state.
3. **`Sampler`** — runs N shots and returns a `Histogram` of measured basis states,
   derived from the probability vector.
4. **Optimizers** — a small `Optimizer` interface plus concrete `GradientDescent` and
   `SPSA` implementations, and a `CommonsMathOptimizer` adapter (COBYLA / Nelder–Mead).
5. **Dynamic circuits** — `ClassicalRegister`, an `If(creg == value, gate)` wrapper,
   and `While` / repeat-until-success driven by mid-circuit measurement.

**Design constraints derived from the existing code:**

- The simulator (`local/SimpleQuantumExecutionEnvironment.runProgram`) returns a
  `Result` whose `getProbability()` is the **final complex state-vector**
  (amplitudes, *not* squared) — see Grover's `searchProbabilities` in
  `algorithm/Classic.java:198-204`, which calls `res.getProbability()` then `.abssqr()`
  per entry. The Estimator/Sampler must read this same vector.
- `runProgram` always calls `result.measureSystem()` at the end
  (`SimpleQuantumExecutionEnvironment.java:124`), which sets a *random* per-qubit
  measurement. That randomness does **not** alter the stored `probability` vector, so
  the Estimator can ignore it; the Sampler will re-sample from the vector itself rather
  than calling `measureSystem` N times (much cheaper, and avoids re-running the circuit).
- **Qubit/bit ordering**: in the state-vector, basis index `j` maps to qubit `q` via
  weight `1 << (nQubits - q - 1)` (qubit 0 is the most-significant bit). This is
  visible in `SimpleQuantumExecutionEnvironment.runProgram:77-87` (init loop,
  `pw = nQubits - j - 1`) and in `Result.measureSystem:208-211` (which iterates the
  *opposite* way — `sel % 2` reads the least-significant bit as qubit 0). **This
  inconsistency must be pinned down with a test before the Estimator is written** (see
  Risks). The Estimator's Pauli evaluation indexes into the state vector, so it must use
  one consistent convention; we adopt the `runProgram` init convention (qubit 0 = MSB).
- `Complex` (`Complex.java`) stores `float r, i`; has `add`, `min`, `mul(Complex)`,
  `mul(double)`, `abssqr()`. There is **no instance `conjugate()`** — only a static
  `Complex.conjugateTranspose(Complex[][])`. The Estimator needs `conj(a)` for the
  `⟨ψ|...|ψ⟩` inner product; add a tiny private helper (or a `Complex.conjugate()`
  instance method) rather than depending on matrix transpose.

**Non-goals for this section**: noise models (Section 5), real-hardware shot sampling
(Section 6). Sampler shots are simulated from the exact distribution.

---

## 2. Current Execution / Result Analysis (cited)

| Concern | Where | Behaviour we rely on |
|---|---|---|
| Program structure | `Program.java:53-138` | `Program(int nQubits, Step...)`, `addStep`, `getSteps()`, `getNumberQubits()`, `getInitialAlphas()`. `addStep` runs `ensureMeasuresafe` (`Program.java:140-158`) which forbids re-superposing a measured qubit. |
| Step structure | `Step.java:50-99` | `Step(Gate...)` / `Step(String, Gate...)`, `addGate`, `getGates()` (unmodifiable). `Step.Type` enum has `NORMAL, PSEUDO, PROBABILITY`. `verifyUnique` (`Step.java:263-268`) forbids two gates on the same qubit in one step. |
| Run + result | `SimpleQuantumExecutionEnvironment.java:65-127` | builds init vector (`:73-88`), decomposes steps (cached on `Program`, `:90-97`), applies each step (`applyStep`, `:145-166`), stores intermediate probabilities, finally `measureSystem()`. |
| Final state vector | `Result.getProbability()` (`Result.java:134-136`) | returns `Complex[]` of length `2^n` = the final **amplitudes**. Set via `setIntermediateProbability` (`Result.java:144-150`) which also keeps `this.probability = p`. |
| Probability extraction (existing pattern) | `algorithm/Classic.java:198-204` | `Complex[] prob = res.getProbability(); answer[i] = prob[i].abssqr();` — the canonical way to get `|amplitude|^2` per basis state. Estimator/Sampler reuse this. |
| Per-qubit marginal | `Result.calculateQubitStatesFromVector` (`Result.java:164-179`) | marginal P(qubit i = 1); useful for single-qubit `⟨Z⟩` sanity but NOT general (correlated Z⊗Z needs the full vector). |
| Mid-circuit probe | `gate/ProbabilitiesGate.java` + `SimpleQuantumExecutionEnvironment.applyStep:149-153` | a `ProbabilitiesGate` in a step captures the live state vector (`setProbabilites(vector)`) without altering it — model for the Estimator's "read state without collapse". |
| Mid-circuit measurement | `gate/ImmediateMeasurement.java` | carries a `Consumer<Boolean>` callback invoked when the qubit is measured. This is the only existing hook for classical feedback and is the basis for dynamic circuits. **Note:** the current `SimpleQuantumExecutionEnvironment` does not appear to actually fire this consumer (no reference to `ImmediateMeasurement` in `applyStep`); wiring it is part of the dynamic-circuit work item. |
| Measurement gate | `gate/Measurement.java` | identity matrix + `applyOptimize` returns `v` unchanged (`Measurement.java:71-79`); collapse is handled at the `Result` level, not per-gate. |

**Key takeaway:** the simulator already produces and preserves the full final state
vector. The Estimator computes `⟨ψ|H|ψ⟩` analytically from that vector; the Sampler
draws from `|amplitude|^2`. Neither needs to modify the simulator core. Dynamic circuits
*do* need executor changes because they require interleaving classical decisions between
quantum steps.

---

## 3. Detailed Work Items

New package: `org.redfx.strange.hybrid` → directory `hybrid/` at repo root, mirroring
the existing flat layout (`gate/`, `local/`, `algorithm/`). Dynamic-circuit gate
wrappers go under `gate/` to sit next to `Measurement`/`ProbabilitiesGate`.

### WI-1 — `Parameter` + `ParametricProgram`

**Files:**
- `hybrid/Parameter.java`
- `hybrid/ParametricProgram.java`

**`Parameter`** — an immutable named symbol for a free angle.

```java
package org.redfx.strange.hybrid;

public final class Parameter {
    private final String name;
    public Parameter(String name) { this.name = Objects.requireNonNull(name); }
    public String getName() { return name; }
    // equals/hashCode on name so it can key a Map
}
```

**`ParametricProgram`** — a template that records the number of qubits, declared
parameters, and a list of *step factories*. A step factory is a function
`Map<String,Double> → Step` so that any gate angle can be a function of the bound
parameters. Binding produces a concrete `Program`.

```java
public final class ParametricProgram {
    private final int nQubits;
    private final List<Parameter> parameters = new ArrayList<>();
    private final List<Function<Map<String,Double>, Step>> stepFactories = new ArrayList<>();

    public ParametricProgram(int nQubits) { this.nQubits = nQubits; }

    public Parameter param(String name) {            // declare + return
        Parameter p = new Parameter(name);
        parameters.add(p);
        return p;
    }

    /** Add a step whose gates may depend on bound parameter values. */
    public ParametricProgram addStep(Function<Map<String,Double>, Step> factory) {
        stepFactories.add(factory);
        return this;
    }

    /** Convenience: a fixed (non-parametric) step. */
    public ParametricProgram addStep(Step fixed) {
        return addStep(binding -> fixed);
    }

    public List<Parameter> getParameters() { return List.copyOf(parameters); }

    /** Bind concrete values and materialise a runnable Program. */
    public Program bind(Map<String,Double> values) {
        validateBinding(values);
        Program p = new Program(nQubits);
        for (var f : stepFactories) p.addStep(f.apply(values));
        return p;
    }

    /** Bind from an ordered double[] aligned with getParameters() order — what optimizers pass. */
    public Program bind(double[] theta) {
        Map<String,Double> m = new LinkedHashMap<>();
        for (int i = 0; i < parameters.size(); i++) m.put(parameters.get(i).getName(), theta[i]);
        return bind(m);
    }
}
```

`validateBinding` throws if a declared parameter is missing. Because `bind` always
constructs a fresh `Program`, the simulator's per-`Program` `decomposedSteps` cache
(`Program.java:62`, `SimpleQuantumExecutionEnvironment.java:90-97`) stays correct — no
cache-invalidation issue.

**Dependency on Section 1:** the ergonomic form, where a single gate object is built
with a *bound* angle, relies on the Section-1 `ParametricGate` interface
(`getParameter()/setParameter()`) so that an angle-bearing gate can be cloned with a new
angle without rebuilding. Until Section 1 lands, the step-factory form above is
self-sufficient: the factory simply does `new RotationY(values.get("theta"), q)`
(`gate/RotationY.java` constructor `RotationY(double theta, int idx)`). **Flag:** when
Section 1 ships `ParametricGate`, refactor factories to `gate.bind(name → value)` to
avoid reconstructing whole `Step`s.

**Tests:** `ParametricProgramTest` — declare two params, bind, assert the produced
`Program` has the expected gate angles and number of steps; assert missing-binding
throws.

---

### WI-2 — `Pauli`, `PauliString`, `Hamiltonian`

**Files:**
- `hybrid/Pauli.java` (enum `I, X, Y, Z`)
- `hybrid/PauliString.java`
- `hybrid/Hamiltonian.java`

A **`PauliString`** is a tensor product of single-qubit Paulis with a real coefficient,
e.g. `0.5 * X0 Z2`. Represent it as a coefficient plus a `Pauli[]` indexed by qubit
(length = nQubits, `I` where the operator acts trivially).

```java
public final class PauliString {
    private final double coefficient;
    private final Pauli[] ops;          // ops[q] is the Pauli acting on qubit q
    public PauliString(double coeff, Pauli[] ops) { ... }

    // Fluent builder for sparse construction:
    //   PauliString.of(nQubits, 0.5).with(0, Pauli.X).with(2, Pauli.Z);
    public static Builder of(int nQubits, double coeff) { ... }

    public double getCoefficient() { return coefficient; }
    public Pauli at(int qubit) { return ops[qubit]; }
    public int nQubits() { return ops.length; }
}
```

A **`Hamiltonian`** is a sum of `PauliString`s (all on the same qubit count):

```java
public final class Hamiltonian {
    private final List<PauliString> terms;
    public Hamiltonian(List<PauliString> terms) { ... }   // validate equal nQubits
    public List<PauliString> getTerms() { return List.copyOf(terms); }
    public int nQubits() { return terms.get(0).nQubits(); }
    public Hamiltonian add(PauliString t) { ... }
    // convenience constants, e.g. Hamiltonian.z(nQubits, qubit) → single Z term, coeff 1
}
```

**Tests:** builder produces correct `ops[]`; `add` accumulates terms; mismatched
qubit counts throw.

---

### WI-3 — `Estimator` (exact expectation from the state vector)

**File:** `hybrid/Estimator.java`

**Goal:** `⟨ψ|H|ψ⟩ = Σ_k coeff_k · ⟨ψ| P_k |ψ⟩` with each `P_k` a Pauli string. We
compute each `⟨ψ| P |ψ⟩` analytically from the amplitude vector `ψ = res.getProbability()`
— no sampling, no collapse (exactly the read-without-collapse property of
`ProbabilitiesGate`).

**Expectation algorithm for one `PauliString` P (coeff factored out):**

For each computational basis index `j` (0…2^n−1) with amplitude `a_j = ψ[j]`:
1. Determine the basis index `j' = perm(j)` that `P|j⟩` maps to, and the scalar phase
   `φ_j ∈ {+1, −1, +i, −i}` such that `P|j⟩ = φ_j |j'⟩`. Single-qubit rules
   (qubit `q`, bit `b_q` of `j` under the **MSB-is-qubit-0** convention,
   weight `1 << (n-q-1)`):
   - `I`: no flip, phase `+1`.
   - `X`: flip bit `q`, phase `+1`.
   - `Z`: no flip, phase `+1` if `b_q==0` else `−1`.
   - `Y`: flip bit `q`, phase `+i` if `b_q==0` else `−i`  (Y|0⟩=+i|1⟩, Y|1⟩=−i|0⟩).
   Compose across all qubits: `j'` = `j` with all X/Y target bits flipped; total phase =
   product of per-qubit phases (a `Complex`).
2. The contribution to `⟨ψ|P|ψ⟩` is `conj(a_{j'}) · φ_j · a_j`.
   (Standard: `⟨ψ|P|ψ⟩ = Σ_j conj(ψ[j']) φ_j ψ[j]` since `⟨ψ|P|ψ⟩ = Σ_j ψ*_{...} ...`.)
3. Sum over all `j`. The imaginary part must be ≈0 for a Hermitian `P`; return the real
   part (assert `|imag| < 1e-9`).

```java
public final class Estimator {
    private final QuantumExecutionEnvironment qee;   // DI, defaults to SimpleQuantumExecutionEnvironment
    public Estimator() { this(new SimpleQuantumExecutionEnvironment()); }
    public Estimator(QuantumExecutionEnvironment qee) { this.qee = qee; }

    /** Run program once, then evaluate ⟨H⟩ on the resulting state vector. */
    public double expectation(Program program, Hamiltonian h) {
        Result r = qee.runProgram(program);
        return expectation(r.getProbability(), h);
    }

    /** Evaluate against an already-computed amplitude vector. */
    public double expectation(Complex[] psi, Hamiltonian h) {
        double total = 0;
        for (PauliString term : h.getTerms())
            total += term.getCoefficient() * pauliExpectation(psi, term);
        return total;
    }

    private double pauliExpectation(Complex[] psi, PauliString p) {
        int n = p.nQubits();
        double re = 0, im = 0;
        for (int j = 0; j < psi.length; j++) {
            int target = j;
            Complex phase = Complex.ONE;
            for (int q = 0; q < n; q++) {
                int weight = 1 << (n - q - 1);
                boolean bit = (j & weight) != 0;
                switch (p.at(q)) {
                    case I: break;
                    case X: target ^= weight; break;
                    case Z: if (bit) phase = phase.mul(-1d); break;
                    case Y: target ^= weight;
                            phase = phase.mul(bit ? new Complex(0,-1) : new Complex(0,1)); break;
                }
            }
            // contribution = conj(psi[target]) * phase * psi[j]
            Complex c = conj(psi[target]).mul(phase).mul(psi[j]);
            re += c.r; im += c.i;
        }
        assert Math.abs(im) < 1e-6 : "non-Hermitian expectation imag="+im;
        return re;
    }

    private static Complex conj(Complex z) { return new Complex(z.r, -z.i); }
}
```

**Notes / accuracy:** `Complex` uses `float` internally (`Complex.java:69-70`), so the
Estimator accumulates in `double` (`re`, `im`) to limit error. Tolerances in tests
should be ~1e-4, not 1e-12.

**Optional optimization (defer):** group commuting Pauli terms / reuse `target` parity
to avoid the inner `q`-loop per term. Not needed for correctness; note as a perf TODO.

**Tests** (`EstimatorTest`):
- `⟨Z⟩` on `|0⟩` (empty 1-qubit program) = **+1**; on `|1⟩` (X gate) = **−1**.
- `⟨X⟩` on `|+⟩` (Hadamard) = **+1**.
- `⟨Z0 Z1⟩` on a Bell state (`H0; CNOT(0,1)`) = **+1** (perfectly correlated).
- `⟨X0 X1⟩` on the same Bell state = **+1**. These two distinguish the full-vector
  computation from a naive per-qubit-marginal shortcut.

---

### WI-4 — `Sampler` (shot histogram)

**File:** `hybrid/Sampler.java`, plus `hybrid/Histogram.java`.

Run the program once to get the exact distribution `p_j = |ψ[j]|^2` (same extraction as
`Classic.searchProbabilities`, `algorithm/Classic.java:199-203`), then draw `shots`
samples by inverse-CDF — mirroring `Result.measureSystem` (`Result.java:188-212`) but
*without* re-running the circuit per shot.

```java
public final class Sampler {
    private final QuantumExecutionEnvironment qee;
    private final java.util.Random rng;
    public Sampler() { this(new SimpleQuantumExecutionEnvironment(), new Random()); }
    public Sampler(QuantumExecutionEnvironment qee, Random rng) { ... }

    public Histogram sample(Program program, int shots) {
        Complex[] psi = qee.runProgram(program).getProbability();
        double[] cdf = new double[psi.length];
        double run = 0;
        for (int i = 0; i < psi.length; i++) { run += psi[i].abssqr(); cdf[i] = run; }
        int[] counts = new int[psi.length];
        for (int s = 0; s < shots; s++) {
            double x = rng.nextDouble() * run;          // run ≈ 1.0; normalise defensively
            int idx = lowerBound(cdf, x);
            counts[idx]++;
        }
        return new Histogram(counts, program.getNumberQubits());
    }
}
```

**`Histogram`** holds `int[] counts` indexed by basis state, exposes `count(int basis)`,
`probability(int basis)` (= count/shots), `mostFrequent()`, and `asBitStringMap()`
(maps basis index → bitstring using the **same qubit/bit convention** the Estimator
adopts; keep a single shared helper so they cannot diverge — see Risks).

**Tests** (`SamplerTest`, seeded `Random` for determinism):
- `|0⟩` 1-qubit, 1000 shots → all counts on basis 0.
- `|+⟩` (Hadamard), 10000 shots → counts[0] and counts[1] each ≈ 5000 (±5%).
- Bell state, 10000 shots → only basis `00` and `11` populated; `01`/`10` ≈ 0.

---

### WI-5 — Optimizer interface + SPSA + GradientDescent + Commons-Math adapter

**Files:**
- `hybrid/optimizer/Optimizer.java`
- `hybrid/optimizer/OptimizerResult.java`
- `hybrid/optimizer/GradientDescent.java`
- `hybrid/optimizer/SPSA.java`
- `hybrid/optimizer/CommonsMathOptimizer.java`

**Interface** — minimise a scalar objective over `double[]` parameters:

```java
public interface Optimizer {
    OptimizerResult minimize(java.util.function.Function<double[],Double> objective,
                             double[] initial);
}

public final class OptimizerResult {
    public final double[] optimalParameters;
    public final double optimalValue;
    public final int iterations;
    public final List<Double> history;   // value per iteration, for convergence plots
}
```

The objective passed in by VQE/QAOA is `theta -> estimator.expectation(parametric.bind(theta), H)`.

**`GradientDescent`** — finite-difference gradient (central difference, step `eps`),
fixed learning rate `lr`, `maxIter` iterations or `|Δvalue| < tol` early stop.
Gradient costs `2·d` circuit evaluations per step (d = #params); document this cost.
(When Section 1 parametric gates land, this can be upgraded to the analytic
**parameter-shift rule** — note as a follow-up.)

**`SPSA`** — Simultaneous Perturbation Stochastic Approximation. Per iteration uses only
**2** objective evaluations regardless of `d`, which is why it is the workhorse for
noisy/expensive VQE:
- gains `a_k = a/(k+1+A)^alpha`, `c_k = c/(k+1)^gamma`
  (standard defaults `alpha=0.602`, `gamma=0.101`, `A ≈ 0.1·maxIter`);
- random `Δ_k ∈ {−1,+1}^d` (Bernoulli);
- `g_k = (f(θ+c_kΔ) − f(θ−c_kΔ)) / (2 c_k) · Δ_k^{-1}`  (componentwise, `Δ^{-1}=Δ`);
- `θ ← θ − a_k g_k`.
Expose `a, c, alpha, gamma, A, maxIter` and a seedable `Random` in the constructor.

**`CommonsMathOptimizer`** — adapter to Apache Commons Math
(`org.apache.commons.math3.optim`). Wrap our `Function<double[],Double>` in a
`MultivariateFunction`, delegate to a gradient-free optimizer
(`SimplexOptimizer` + `NelderMeadSimplex`, the closest readily-available analog to
COBYLA in Commons Math; true COBYLA is not in commons-math3, so document this
substitution), translate the `PointValuePair` back to `OptimizerResult`.

**Build dependency:** add Apache Commons Math to `pom.xml`
(`org.apache.commons:commons-math3:3.6.1`). The adapter must live in its own class so the
core hybrid package has **no** hard dependency on commons-math (keep it optional);
`GradientDescent`/`SPSA` have zero third-party deps.

**Tests:**
- Each optimizer minimises a trivial convex quadratic `f(x)=Σ(x_i−t_i)^2` to near `t`
  (tolerance loose for SPSA, tight for GD/NelderMead).
- Integration (lives in Section-3 VQE tests but stubbed here): a 1-qubit "energy"
  `f(θ)=⟨Z⟩` on `RotationY(θ)|0⟩ = cos θ` is minimised toward `θ≈π` (value `≈−1`).

---

### WI-6 — Dynamic circuits: `ClassicalRegister`, conditionals, control flow

This is the only work item that touches the **executor**. Mid-circuit measurement must
write a classical bit, and subsequent quantum operations may be gated on it.

**Files:**
- `hybrid/ClassicalRegister.java`
- `gate/ClassicalControlledGate.java`  (the `If(creg==value, gate)` wrapper)
- executor changes in `local/SimpleQuantumExecutionEnvironment.java` and/or a new
  `local/DynamicExecutionEnvironment.java` subclass (preferred — keep the base simulator
  untouched to avoid regressions).
- `hybrid/DynamicProgram.java` for `While` / repeat-until-success loop bodies.

**`ClassicalRegister`** — a mutable named array of classical bits living *outside* the
quantum state:

```java
public final class ClassicalRegister {
    private final String name;
    private final boolean[] bits;
    public ClassicalRegister(String name, int size) { ... }
    public void set(int i, boolean v); public boolean get(int i);
    public int value();          // little-endian integer view, for == comparison
    public void reset();
}
```

**Mid-circuit measurement → classical write.** Reuse the existing
`ImmediateMeasurement` (`gate/ImmediateMeasurement.java`), whose `Consumer<Boolean>`
callback is the natural sink. Add a factory
`Gate measureInto(int qubit, ClassicalRegister creg, int bit)` that returns
`new ImmediateMeasurement(qubit, b -> creg.set(bit, b))`. **The executor must actually
collapse the qubit and fire the consumer** — currently `applyStep`
(`SimpleQuantumExecutionEnvironment.java:145-166`) has no `ImmediateMeasurement`
branch, so add one: on encountering an `ImmediateMeasurement`, sample that single
qubit's marginal from the live vector (analogous to `Result.measureSystem`, but for one
qubit), **renormalise/project** the state vector onto the measured outcome, then invoke
the consumer with the sampled boolean.

**`ClassicalControlledGate`** — `If(creg==value, gate)`:

```java
public final class ClassicalControlledGate implements Gate {   // delegates to wrapped gate
    private final ClassicalRegister creg;
    private final int expectedValue;
    private final Gate inner;
    public boolean isActive() { return creg.value() == expectedValue; }
    // getMatrix(): identity if !isActive(), else inner.getMatrix(); index/affected-qubits delegate to inner
}
```

The executor checks `isActive()` at apply time (after any preceding mid-circuit
measurement in the same run has updated `creg`) and applies either `inner` or identity.
Because the active/inactive decision is data-dependent, **a dynamic program cannot be
fully decomposed up-front** — the executor must evaluate steps sequentially and consult
the register between steps. This is why a dedicated `DynamicExecutionEnvironment` that
loops over `program.getSteps()` directly (resolving conditionals just before
`applyStep`) is cleaner than retrofitting the cached-decomposition path.

**`While` / repeat-until-success.** Model as a loop construct in `DynamicProgram`:
a loop body (a `Program` fragment) + a predicate over a `ClassicalRegister` + a
`maxIterations` guard. The dynamic executor runs the body, evaluates the predicate
against the register written by the body's `ImmediateMeasurement`s, and repeats until
the predicate is satisfied or the iteration cap is hit. The canonical
repeat-until-success pattern: apply a gadget, measure an ancilla into `creg`; if it
read the "failure" value, apply a fixup and retry.

**Tests** (`DynamicCircuitTest`):
- `measureInto` on `|1⟩` writes `true` and collapses to `|1⟩` (probability 1 on basis 1
  afterwards).
- `If(creg==1, X(0))`: prepare ancilla to `|1⟩`, measure into `creg`, conditionally
  apply X to a target initially `|0⟩` → target deterministically `|1⟩`. With ancilla
  `|0⟩` → target stays `|0⟩`.
- Repeat-until-success: a body that "succeeds" with p=0.5 each round terminates within
  `maxIterations` and leaves `creg` in the success value (use a seeded RNG so the test is
  deterministic).

**Measure-safety interaction:** `Program.addStep`'s `ensureMeasuresafe`
(`Program.java:140-158`) forbids re-superposing a measured qubit. Conditional gates and
RUS loops legitimately re-use qubits across mid-circuit measurements, so dynamic programs
must either bypass that check (add via a dynamic-program builder that doesn't route
through `Program.addStep`) or relax it for `ImmediateMeasurement` ancillas. **Flag and
decide explicitly** — silently re-superposing a `Measurement`-collapsed qubit is exactly
what that guard exists to prevent.

---

## 4. Cross-Section Dependencies

- **Depends on Section 1 (parametric gates).** `ParametricProgram` is *ergonomic* with a
  `ParametricGate` interface (`setParameter`/`getParameter`) and analytic parameter-shift
  gradients. Until then, WI-1 uses step factories that reconstruct gates via existing
  constructors (`RotationY(theta, idx)` etc., `gate/RotationY.java`). **Flagged in WI-1.**
- **Consumed by Section 3 (VQE / QAOA).** Those algorithms in `algorithm/` are thin
  drivers: build a `ParametricProgram` ansatz, define a `Hamiltonian`, then
  `optimizer.minimize(theta -> estimator.expectation(ansatz.bind(theta), H), theta0)`.
  This section must land before VQE/QAOA. **Flagged.**
- **`AlgorithmResult` (Section 3).** `OptimizerResult.history` should be compatible with
  the planned structured `AlgorithmResult` so convergence data flows through.
- **Estimator/Sampler vs. hardware backends (Section 6).** On real hardware there is no
  state vector, so `Estimator` would need a sampling-based fallback (measure in rotated
  bases). Keep the `Estimator` API backend-agnostic (`QuantumExecutionEnvironment` DI) so
  a future `SamplingEstimator` can implement the same contract. Note, do not build now.
- **`Complex.conjugate`.** Adding a one-line instance `conjugate()` to `Complex.java`
  benefits both this section and Section 8 (tomography). Otherwise keep the private helper
  local to `Estimator`.

---

## 5. Testing Strategy (summary)

| Component | Test | Expected |
|---|---|---|
| Estimator | `⟨Z⟩` on `|0⟩` | +1 |
| Estimator | `⟨Z⟩` on `|1⟩` (X gate) | −1 |
| Estimator | `⟨X⟩` on `|+⟩` (H) | +1 |
| Estimator | `⟨Z0Z1⟩`, `⟨X0X1⟩` on Bell state | +1 (validates full-vector math) |
| Sampler | `|+⟩`, 10k shots | ~50/50 within 5% |
| Sampler | Bell, 10k shots | only `00`/`11` |
| Optimizers | minimise `Σ(x−t)²` | converge to `t` |
| Optimizers | minimise `⟨Z⟩=cos θ` on `Ry(θ)|0⟩` | θ→π, value→−1 |
| Dynamic | `measureInto` collapses state | prob 1 on measured basis |
| Dynamic | `If(creg==1, X)` | conditional flip is deterministic |
| Dynamic | RUS loop (seeded RNG) | terminates ≤ maxIter, success value set |
| Integration (VQE smoke) | 2-qubit H2 toy Hamiltonian, hardware-efficient `Ry` ansatz, SPSA | converges near known ground-state energy (loose tol) |

Mirror existing test conventions in the module; verify gate constructor signatures
against the real files before writing tests (e.g. `RotationY(double, int)`,
`Program(int, Step...)`, `Step(Gate...)`). Tolerances ~1e-4 due to `Complex` being
`float`-backed (`Complex.java:69-70`).

**H2 toy Hamiltonian** for the VQE smoke test (standard minimal example, 2 qubits):
`H = g0·I + g1·Z0 + g2·Z1 + g3·Z0Z1 + g4·X0X1` with literature coefficients at a fixed
bond length; assert the optimizer reaches within a small margin of the exact minimum
eigenvalue (compute the exact eigenvalue independently in the test by diagonalising the
4×4 matrix, or hard-code the known value).

---

## 6. Risks

1. **Qubit/bit-ordering ambiguity.** `runProgram` init (`SimpleQuantumExecutionEnvironment.java:77-87`,
   qubit 0 = MSB) and `Result.measureSystem` (`Result.java:208-211`, qubit 0 = LSB)
   appear to use *opposite* conventions. The Estimator and Sampler index the state vector
   directly, so a wrong convention silently gives correct single-qubit answers but wrong
   correlated ones. **Mitigation:** pin the convention with the `⟨Z0Z1⟩`/Bell test
   (WI-3) and a Sampler bitstring test (WI-4) *first*; centralise bit↔qubit mapping in
   one shared helper used by Estimator, Sampler, and `Histogram.asBitStringMap`.
2. **`Complex` is `float`-backed.** Expectation values accumulate rounding error;
   accumulate in `double` and use loose tolerances. Deep ansätze (many gates) amplify
   this — note as a known limitation.
3. **`ImmediateMeasurement` is not currently fired by the simulator** (no branch in
   `applyStep`). Dynamic circuits depend on adding correct single-qubit collapse +
   renormalisation; getting projection/normalisation wrong corrupts the state. Validate
   with the `measureInto` collapse test before building conditionals.
4. **`ensureMeasuresafe` blocks legitimate RUS re-use** of measured qubits
   (`Program.java:140-158`). Decide explicitly whether dynamic programs bypass or relax
   this guard (WI-6).
5. **Decomposition cache vs. data-dependent steps.** Conditional gates cannot be
   pre-decomposed; do not route dynamic programs through the cached-decomposition path.
   Use a sequential `DynamicExecutionEnvironment`.
6. **Commons Math has no true COBYLA.** Substituting Nelder–Mead simplex changes
   convergence behaviour; document it and keep the dependency optional.
7. **Estimator cost.** `expectation` is O(terms · 2^n) per evaluation, and the optimizer
   calls it many times. For larger qubit counts this dominates runtime; note the
   term-grouping perf TODO.

---

## 7. Suggested Sequencing

1. **WI-2** `Pauli` / `PauliString` / `Hamiltonian` (no deps; pure data types).
2. **WI-3** `Estimator` — write the ordering tests first to lock the bit convention
   (de-risks Risk 1). Add `Complex.conjugate()` or the local helper here.
3. **WI-4** `Sampler` + `Histogram` (shares the bit-mapping helper from WI-3).
4. **WI-1** `Parameter` / `ParametricProgram` (independent of 2–4; can be parallelised,
   but optimizers below consume it so finish before WI-5 integration tests).
5. **WI-5** Optimizers — `Optimizer`/`OptimizerResult`, then `GradientDescent`, `SPSA`,
   then the `CommonsMathOptimizer` adapter (after adding the `pom.xml` dependency).
6. **WI-6** Dynamic circuits — `ClassicalRegister` → wire `ImmediateMeasurement` collapse
   into a new `DynamicExecutionEnvironment` → `ClassicalControlledGate` → `While`/RUS.
   Largest/riskiest (executor changes); do last.
7. Hand off to **Section 3** VQE/QAOA, which compose WI-1 + WI-3 + WI-5.

Items 1–5 require no changes to existing source files (purely additive packages plus an
optional one-line `Complex.conjugate()`); only WI-6 modifies execution, and even there
the recommendation is a *new* `DynamicExecutionEnvironment` subclass rather than editing
`SimpleQuantumExecutionEnvironment`, preserving all current behaviour.
