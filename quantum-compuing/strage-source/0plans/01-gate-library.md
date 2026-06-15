# Plan 01 — Gate Library Completeness

Expansion area: **Section 1 of `ideas.md` — Gate Library Completeness**.

This plan adds the missing standard gates and introduces a unified `ParametricGate`
interface so all angle-bearing gates share a common parameter API. It is written to
match the *actual* conventions observed in the existing `org.redfx.strange.gate`
package — do not deviate from these patterns.

---

## 1. Overview & Goals

### Goals
1. Complete the single-qubit gate set: `S`, `T`, `Sdg` (S†), `Tdg` (T†), `SX` (√X), `SXdg` (√X†), `P`/`Phase` (general phase).
2. Add the missing two-qubit gates: `ISwap`, `SqrtISwap` (√iSWAP), `DCX`, `ECR`, `XX`, `YY`, `ZZ` (Ising interaction gates), and the parametric `CU(θ,φ,λ,γ)` controlled-U.
3. Add the missing three-qubit gates: `Fredkin` (CSWAP), `Deutsch` (parametric 3-qubit), and the reduced/expanded Toffoli variants `RC3X` and `C3X` (the latter as a 4-qubit gate).
4. Introduce a `ParametricGate` interface with `getParameters()` / `setParameter(int, double)` (and convenience single-parameter accessors) and retrofit the angle-bearing gates (`Rotation`, `RotationX/Y/Z`, `R`, `Cr`, the new `P`, `XX`, `YY`, `ZZ`, `CU`, `Deutsch`) onto it.
5. Add static factory methods on the `Gate` interface mirroring the existing pattern (`Gate.x(idx)`, `Gate.cnot(a,b)`, etc.) for every new gate.
6. Provide unitarity and known-state tests for every new gate.

### Non-goals
- No changes to the simulation engine (`Computations.java`, `SimpleQuantumExecutionEnvironment`). All new gates must work through the existing `getMatrix()` dense-matrix path.
- No transpiler / decomposition work (that is Section 4). Gates here are defined by an explicit unitary matrix, exactly like `X`, `Swap`, `Toffoli`.

### Conventions observed (must be followed)
- **License header**: every source file under `gate/` begins with the `/*- #%L Strange %% Copyright (C) 2020 Johan Vos %% ... #L% */` block (see `gate/X.java` lines 1–32). The newer rotation files (`Rotation.java`, `RotationX/Y/Z.java`) omit it; for new files, **include the full header** to match the dominant style of matrix-bearing gates.
- **Package**: `package org.redfx.strange.gate;`
- **Base classes**:
  - 1 qubit → `extends SingleQubitGate` (group `"SingleQubit"`, size 1).
  - 2 qubits → `extends TwoQubitGate` (group `"TwoQubit"`, size 2, 4×4 matrix).
  - 3 qubits → `extends ThreeQubitGate` (group `"ThreeQubit"`, 8×8 matrix; note `ThreeQubitGate` does **not** define `getSize()`, so each concrete class overrides `getSize()` returning 3, as `Toffoli` does).
- **Matrix definition**: a field `Complex[][] matrix = new Complex[][]{ ... }` initialised inline using `Complex.ZERO`, `Complex.ONE`, `Complex.I`, `Complex.HC`, `Complex.HCN`, or `new Complex(re, im)`; `getMatrix()` returns it. See `X.java`, `Cnot.java`, `Cz.java`, `Swap.java`, `Toffoli.java`.
- **Inverse handling**: gates that are their own inverse / fixed (`X`, `Cnot`, `Swap`) inherit the base no-op `setInverse`. `Toffoli` explicitly overrides `setInverse` as a NOP. Gates whose inverse differs (`Rotation`, `R`, `SingleQubitMatrixGate`) override `setInverse` to call `super.setInverse(v)` then `matrix = Complex.conjugateTranspose(matrix);`. **Use the same `conjugateTranspose` trick for every non-self-inverse new gate.**
- **Caption**: short symbol via `@Override public String getCaption()` (e.g. `"X"`, `"Cnot"`, `"CCnot"`). Parametric gates include the angle in the caption (e.g. `RotationZ` → `"RotationZ " + thetav`).
- **Complex API available** (`Complex.java`): constants `ZERO, ONE, I, HC (=1/√2), HCN (=-1/√2)`; instance methods `add, min, mul(Complex), mul(double)`; statics `identityMatrix(dim)`, `tensor`, `mmul`, `conjugateTranspose`. There is **no** `exp`/`expi` helper — build phases with `new Complex(Math.cos(φ), Math.sin(φ))` exactly as `R.java` does.

---

## 2. Prerequisites / Dependencies

| Prerequisite | Detail |
|---|---|
| `Complex` constants & `conjugateTranspose` | Already present (`Complex.java` lines 54–67, 269). No change needed. |
| `SingleQubitGate`, `TwoQubitGate`, `ThreeQubitGate` | Already present. New 3-qubit gates reuse `ThreeQubitGate` as-is. |
| **New `FourQubitGate` base class** | Required only for `C3X` (4-qubit, 16×16). Must be authored (no 4-qubit base exists). See work item 4.3. Alternatively `C3X` can be deferred — see Risks. |
| `Gate` interface | Add static factory methods (work item 5). Backwards compatible — only additions. |
| `ParametricGate` interface | New file (work item 1). Pure addition; retrofitting existing gates is additive (gates `implements ParametricGate` in addition to extending their base). |

No build-system change is needed: files are flat at repo root and compiled together; new `.java` files under `gate/` are picked up automatically.

---

## 3. Detailed Work Items

Ordered for incremental, low-risk delivery. Each item lists: file path(s), base class, the unitary, caption, inverse handling, and a code sketch in the existing style.

> Notation: `c = cos(θ/2)`, `s = sin(θ/2)` for interaction gates; `ω = e^{iφ} = new Complex(cos φ, sin φ)`.

### 3.0 — `ParametricGate` interface (do first; enables retrofits)

**File**: `ParametricGate.java` (repo root, package `org.redfx.strange`).

Rationale: it lives alongside `Gate.java` (same package `org.redfx.strange`), because the angle-bearing gate classes in `org.redfx.strange.gate` already import `org.redfx.strange.*`.

```java
package org.redfx.strange;

import java.util.List;

/**
 * A ParametricGate is a {@link Gate} whose unitary depends on one or more real
 * (angle) parameters. Implementing this interface lets circuit-optimisation and
 * variational algorithms (VQE, QAOA) read and rewrite gate angles uniformly.
 */
public interface ParametricGate extends Gate {

    /** @return the ordered list of real parameters this gate currently holds. */
    List<Double> getParameters();

    /**
     * Set the parameter at position {@code index} and recompute the gate matrix.
     * @param index 0-based parameter position
     * @param value the new angle / phase value
     */
    void setParameter(int index, double value);

    /** @return the number of parameters this gate has. */
    default int getParameterCount() { return getParameters().size(); }

    /** Convenience accessor for single-parameter gates. */
    default double getParameter() { return getParameters().get(0); }

    /** Convenience setter for single-parameter gates. */
    default void setParameter(double value) { setParameter(0, value); }
}
```

Design note: `setParameter` must **rebuild the matrix field** from the new value(s).
Because existing rotation classes store `matrix` as a (currently `final`-ish inline)
field, the retrofit changes that field to be reassignable and extracts matrix
construction into a private `buildMatrix()` helper. See item 3.6.

### 3.1 — Phase family: `S`, `T`, `Sdg`, `Tdg`, `P` (`Phase`)

**Base**: `SingleQubitGate`. **Files**: `gate/S.java`, `gate/T.java`, `gate/Sdg.java`, `gate/Tdg.java`, `gate/P.java`.

Unitaries (diagonal `[[1,0],[0, e^{iφ}]]`):

| Gate | φ | (1,1) entry | Caption |
|---|---|---|---|
| `S`   | π/2  | `Complex.I` | `"S"` |
| `Sdg` | −π/2 | `Complex.I.mul(-1)` | `"S†"` |
| `T`   | π/4  | `new Complex(HV, HV)` where `HV=1/√2` → `new Complex(Math.cos(Math.PI/4), Math.sin(Math.PI/4))` | `"T"` |
| `Tdg` | −π/4 | `new Complex(Math.cos(Math.PI/4), -Math.sin(Math.PI/4))` | `"T†"` |
| `P`   | θ (ctor arg) | `new Complex(Math.cos(θ), Math.sin(θ))` | `"P " + theta` |

`S`, `T`, `Sdg`, `Tdg` are fixed; **do not** override `setInverse` (inherit base no-op). Sketch (`S.java`):

```java
package org.redfx.strange.gate;

import org.redfx.strange.Complex;

public class S extends SingleQubitGate {

    Complex[][] matrix = new Complex[][]{
        {Complex.ONE, Complex.ZERO},
        {Complex.ZERO, Complex.I}
    };

    public S(int idx) { super(idx); }

    @Override public Complex[][] getMatrix() { return matrix; }

    @Override public String getCaption() { return "S"; }
}
```

`P` is the general phase gate and **is a `ParametricGate`** (single parameter θ). It
follows the `R.java` pattern exactly (store `expv`, build diagonal phase). It overrides
`setInverse` with the `conjugateTranspose` trick and implements `ParametricGate`:

```java
public class P extends SingleQubitGate implements ParametricGate {
    private double phi;
    private Complex[][] matrix;

    public P(double phi, int idx) { super(idx); this.phi = phi; buildMatrix(); }

    private void buildMatrix() {
        matrix = new Complex[][]{
            {Complex.ONE, Complex.ZERO},
            {Complex.ZERO, new Complex(Math.cos(phi), Math.sin(phi))}
        };
    }
    @Override public Complex[][] getMatrix() { return matrix; }
    @Override public void setInverse(boolean v) { super.setInverse(v); matrix = Complex.conjugateTranspose(matrix); }
    @Override public String getCaption() { return "P " + phi; }
    @Override public java.util.List<Double> getParameters() { return java.util.List.of(phi); }
    @Override public void setParameter(int index, double value) { this.phi = value; buildMatrix(); }
}
```

> Note: `S == P(π/2)`, `T == P(π/4)`. Keeping the explicit fixed classes mirrors the
> library's existing redundancy (`Cz` is a fixed special-case rather than `Cr(π)`).

### 3.2 — `SX` (√X) and `SXdg` (√X†)

**Base**: `SingleQubitGate`. **Files**: `gate/SX.java`, `gate/SXdg.java`. Fixed gates; SX† = conjugate transpose of SX, so author both explicitly OR author `SX` and let `SXdg extends SX` overriding the matrix to the conj-transpose. Prefer two explicit files for clarity (matches the `RotationX`/`RotationZ` "separate file per variant" habit).

√X unitary = ½·`[[1+i, 1−i],[1−i, 1+i]]`:

```java
Complex[][] matrix = new Complex[][]{
    {new Complex(0.5, 0.5), new Complex(0.5, -0.5)},
    {new Complex(0.5, -0.5), new Complex(0.5, 0.5)}
};
// caption "SX"
```

√X† = ½·`[[1−i, 1+i],[1+i, 1−i]]` (caption `"SX†"`). These are fixed (no `ParametricGate`, no `setInverse` override).

### 3.3 — `ISwap` and `SqrtISwap`

**Base**: `TwoQubitGate`. **Files**: `gate/ISwap.java`, `gate/SqrtISwap.java`. Fixed.

iSWAP (basis order |00⟩,|01⟩,|10⟩,|11⟩):
```
[[1,0,0,0],
 [0,0,i,0],
 [0,i,0,0],
 [0,0,0,1]]
```
```java
Complex[][] matrix = new Complex[][]{
    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
    {Complex.ZERO, Complex.ZERO, Complex.I, Complex.ZERO},
    {Complex.ZERO, Complex.I, Complex.ZERO, Complex.ZERO},
    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE}
};
// caption "iSwap"
```

√iSWAP:
```
[[1,0,0,0],
 [0, 1/√2, i/√2, 0],
 [0, i/√2, 1/√2, 0],
 [0,0,0,1]]
```
Use `Complex.HC` for `1/√2` and `new Complex(0, HV)` (i.e. `Complex.HC.mul(Complex.I)`) for `i/√2`. Caption `"√iSwap"`. iSWAP is not self-inverse; if inverse support is wanted, override `setInverse` with the `conjugateTranspose` trick (otherwise inherit base NOP — but prefer the conjugateTranspose override since the inverse differs).

### 3.4 — `DCX` and `ECR`

**Base**: `TwoQubitGate`. **Files**: `gate/DCX.java`, `gate/ECR.java`.

DCX (double-CNOT, cnot(0,1) then cnot(1,0)) — a permutation:
```
[[1,0,0,0],
 [0,0,0,1],
 [0,1,0,0],
 [0,0,1,0]]
```
Caption `"DCX"`. Not self-inverse → override `setInverse` with conjugateTranspose.

ECR (echoed cross-resonance) = (1/√2)·`[[0,0,1,i],[0,0,i,1],[1,-i,0,0],[-i,1,0,0]]`:
```java
Complex a = Complex.HC;            //  1/√2
Complex bi = new Complex(0, HV);   //  i/√2
Complex mbi = new Complex(0, -HV); // -i/√2
Complex[][] matrix = new Complex[][]{
    {Complex.ZERO, Complex.ZERO, a,   bi },
    {Complex.ZERO, Complex.ZERO, bi,  a  },
    {a,            mbi,          Complex.ZERO, Complex.ZERO},
    {mbi,          a,            Complex.ZERO, Complex.ZERO}
};
// caption "ECR"; ECR is Hermitian & unitary → self-inverse, inherit base NOP
```
(`HV = 1/Math.sqrt(2)`; declare a local `final double HV = 1/Math.sqrt(2);` or reuse via `Complex.HC` for real entries.)

### 3.5 — Ising interaction gates `XX`, `YY`, `ZZ` (parametric)

**Base**: `TwoQubitGate`, **implements `ParametricGate`** (single angle θ). **Files**: `gate/RXX.java`, `gate/RYY.java`, `gate/RZZ.java` (names match Qiskit; captions `"XX θ"` etc.). Build matrix in a private `buildMatrix()` so `setParameter` can rebuild.

With `c = cos(θ/2)`, `s = sin(θ/2)`:

RXX = `[[c,0,0,-is],[0,c,-is,0],[0,-is,c,0],[-is,0,0,c]]`
```java
Complex c  = new Complex(Math.cos(theta/2), 0);
Complex ms = new Complex(0, -Math.sin(theta/2)); // -i sin
matrix = new Complex[][]{
    {c, Complex.ZERO, Complex.ZERO, ms},
    {Complex.ZERO, c, ms, Complex.ZERO},
    {Complex.ZERO, ms, c, Complex.ZERO},
    {ms, Complex.ZERO, Complex.ZERO, c}
};
```

RYY = `[[c,0,0,+is],[0,c,-is,0],[0,-is,c,0],[+is,0,0,c]]` (off-diagonal corners use `+i sin = new Complex(0, +sin)`).

RZZ = diagonal `[e^{-iθ/2}, e^{+iθ/2}, e^{+iθ/2}, e^{-iθ/2}]`:
```java
Complex em = new Complex(Math.cos(theta/2), -Math.sin(theta/2));
Complex ep = new Complex(Math.cos(theta/2),  Math.sin(theta/2));
matrix = new Complex[][]{
    {em, Complex.ZERO, Complex.ZERO, Complex.ZERO},
    {Complex.ZERO, ep, Complex.ZERO, Complex.ZERO},
    {Complex.ZERO, Complex.ZERO, ep, Complex.ZERO},
    {Complex.ZERO, Complex.ZERO, Complex.ZERO, em}
};
```
Each overrides `setInverse` (conjugateTranspose) and implements `ParametricGate`
(`getParameters` → `[theta]`, `setParameter(0, v)` → set + `buildMatrix()`).

### 3.6 — Retrofit existing parametric gates onto `ParametricGate`

Files: `gate/Rotation.java`, `gate/RotationX.java`, `gate/RotationY.java`, `gate/RotationZ.java`, `gate/R.java`, `gate/Cr.java`.

- `Rotation`: change `protected final double thetav` → `protected double thetav`; move the `switch (axis)` matrix construction into a private `void buildMatrix()`; have the constructor call it; add `implements ParametricGate` with `getParameters()` → `List.of(thetav)` and `setParameter(0, v)` → `{ this.thetav = v; buildMatrix(); }`. `RotationX/Y/Z` inherit the interface transparently (no extra code) since they only override `getCaption()`.
- `R`: add `implements ParametricGate`; expose `expv` as the single parameter; extract matrix build into `buildMatrix()`. Watch the dual constructor (`R(int base,int pow,int idx)` derives `exp`); `setParameter` sets `expv` directly and clears `pow` semantics (keep simple: only rebuild the matrix).
- `Cr` (controlled phase, `gate/Cr.java`): confirm it stores an angle; retrofit identically. (Read the file before editing to confirm its field names.)

Backwards-compat: existing callers of these classes are unaffected — only additions. The only behavioural change is that the angle field is no longer `final`.

### 3.7 — `Fredkin` (CSWAP) — 3-qubit

**Base**: `ThreeQubitGate`. **File**: `gate/Fredkin.java`. Fixed, self-inverse. 8×8: identity on the first 5 basis states, swap of |101⟩↔|110⟩ (rows/cols 5↔6), identity on |111⟩. Mirror `Toffoli.java` exactly, including `getSize()` returning 3 and a NOP `setInverse`:
```
controlled swap of qubits 2,3 on control qubit 1:
rows 0..4 = e0..e4, row5 -> e6, row6 -> e5, row7 -> e7
```
Caption `"CSwap"`. Add `Gate.fredkin(a,b,c)` factory.

### 3.8 — `Deutsch` (parametric 3-qubit)

**Base**: `ThreeQubitGate`, **implements `ParametricGate`**. **File**: `gate/Deutsch.java`. 8×8 = identity on first 6 basis states; on the |11x⟩ block (rows/cols 6,7) apply `[[i cos θ, sin θ],[sin θ, i cos θ]]`:
```java
Complex icos = new Complex(0, Math.cos(theta)); // i cos θ
Complex sin  = new Complex(Math.sin(theta), 0);
// rows 0..5 identity; row6 = [...,icos@6, sin@7]; row7 = [..., sin@6, icos@7]
```
`getSize()` → 3; `setInverse` via conjugateTranspose; caption `"Deutsch " + theta`. `getParameters()` → `[theta]`.

### 3.9 — `CU` parametric controlled-U — 2-qubit

**Base**: `TwoQubitGate`, **implements `ParametricGate`** (4 params θ,φ,λ,γ). **File**: `gate/CU.java`. Control = first qubit. Block-diagonal: I₂ on the control-0 subspace, U(θ,φ,λ,γ) on the control-1 subspace.

U single-qubit block (Qiskit CU convention):
```
U = e^{iγ} * [[ cos(θ/2),               -e^{iλ} sin(θ/2) ],
              [ e^{iφ} sin(θ/2),  e^{i(φ+λ)} cos(θ/2) ]]
```
4×4:
```java
double c = Math.cos(theta/2), s = Math.sin(theta/2);
Complex eg = new Complex(Math.cos(gamma), Math.sin(gamma));            // e^{iγ}
Complex u00 = eg.mul(new Complex(c, 0));
Complex u01 = eg.mul(new Complex(-Math.cos(lambda)*s, -Math.sin(lambda)*s));   // -e^{iλ} s
Complex u10 = eg.mul(new Complex(Math.cos(phi)*s, Math.sin(phi)*s));           //  e^{iφ} s
Complex u11 = eg.mul(new Complex(Math.cos(phi+lambda)*c, Math.sin(phi+lambda)*c)); // e^{i(φ+λ)} c
matrix = new Complex[][]{
    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
    {Complex.ZERO, Complex.ZERO, u00, u01},
    {Complex.ZERO, Complex.ZERO, u10, u11}
};
```
`getParameters()` → `[theta, phi, lambda, gamma]`; `setParameter(i,v)` switches on `i`; `setInverse` via conjugateTranspose; caption `"CU"`.

> Verify the control-qubit ordering against the simulator's basis convention by a
> known-state test (item 6.3) before trusting the block placement; if the simulator
> treats the *second* listed qubit as the high-order bit, the active block moves to
> rows/cols {1,3} instead of {2,3}. Cross-check against the existing `Cnot` matrix
> (which puts the X-action on rows 2,3) to fix the convention.

### 3.10 — `RC3X` and `C3X`

- `RC3X` (relative-phase / simplified 3-control-ish reduced Toffoli) is a **3-qubit** gate → `extends ThreeQubitGate`, fixed permutation-with-phases 8×8. **File**: `gate/RC3X.java`. Use the standard relative-phase Toffoli matrix. Lower priority — implement after the core set.
- `C3X` is a **4-qubit** gate (3 controls + 1 target, 16×16) and therefore needs a `FourQubitGate` base (see 4.3). **File**: `gate/C3X.java`. Identity 16×16 except rows/cols 14↔15 swapped (X on target when all 3 controls = 1). Caption `"C3X"`; `getSize()` → 4; self-inverse NOP `setInverse`.

---

## 4. Supporting Work

### 4.1 — `Gate` static factories
Add to `Gate.java` (matching existing one-liner style, lines 56–186):
```java
static Gate s(int idx) { return new S(idx); }
static Gate t(int idx) { return new T(idx); }
static Gate sdg(int idx) { return new Sdg(idx); }
static Gate tdg(int idx) { return new Tdg(idx); }
static Gate sx(int idx) { return new SX(idx); }
static Gate phase(double theta, int idx) { return new P(theta, idx); }
static Gate iswap(int a, int b) { return new ISwap(a, b); }
static Gate dcx(int a, int b) { return new DCX(a, b); }
static Gate ecr(int a, int b) { return new ECR(a, b); }
static Gate rxx(double t, int a, int b) { return new RXX(t, a, b); }
static Gate ryy(double t, int a, int b) { return new RYY(t, a, b); }
static Gate rzz(double t, int a, int b) { return new RZZ(t, a, b); }
static Gate cu(double th, double ph, double la, double ga, int c, int t) { return new CU(th, ph, la, ga, c, t); }
static Gate fredkin(int a, int b, int c) { return new Fredkin(a, b, c); }
static Gate deutsch(double theta, int a, int b, int c) { return new Deutsch(theta, a, b, c); }
```
(`gate.*` is already wildcard-imported in `Gate.java` line 35, so no new imports.)

### 4.2 — Javadoc
Match the existing `<p>...</p>` minimal Javadoc on factory methods and constructors. Other projects in the ecosystem run the javadoc-maven plugin (the `$Id: $Id` tags suggest a doc pipeline) — keep class-level `@author` / `@version $Id: $Id` tags consistent with neighbours.

### 4.3 — `FourQubitGate` base (only if `C3X` is in scope)
**File**: `gate/FourQubitGate.java`. Mirror `ThreeQubitGate` (4 fields `first..fourth`, `setAdditionalQubit(idx, cnt)` for cnt∈{1,2,3}, `getAffectedQubitIndexes` → `Arrays.asList(first,second,third,fourth)`, group `"FourQubit"`). Concrete `C3X` overrides `getSize()` → 4. **Verify the simulator (`Computations.java`) supports 4-qubit dense gates** before committing — if it assumes ≤3 contiguous qubits anywhere, defer `C3X` (see Risks).

---

## 5. Testing Strategy

Place tests next to existing gate tests (locate the test source root first — search for an existing `*Test.java` / JUnit usage; if none exists in this snapshot, create `test/.../GateLibraryTest.java` under the project's standard test layout). Each gate gets:

1. **Unitarity check** — for `g.getMatrix()` confirm `M · M† = I` within ε=1e-9 using `Complex.mmul(M, Complex.conjugateTranspose(M))` and asserting near-identity. Add a reusable helper `assertUnitary(Complex[][] m)`.
2. **Determinism / shape** — matrix dimension equals `2^getSize()`.
3. **Known-state checks** (run a tiny `Program`/`Step` through `SimpleQuantumExecutionEnvironment`):
   - `S` then `S` on |+⟩ equals `Z` action; `T` four times = `Z`; `Sdg·S = I`; `Tdg·T = I`.
   - `SX·SX = X` (up to global phase — compare probabilities, not raw amplitudes).
   - `iSwap` on |01⟩ → i|10⟩ (check probability on qubit-1 = 1).
   - `√iSwap · √iSwap = iSwap`.
   - `Fredkin` with control=1 swaps targets; with control=0 leaves them.
   - `DCX` permutation: |01⟩→|11⟩→… verify against explicit cnot·cnot.
   - `CU(θ,0,0,0)` with control=1 reproduces `Rotation(θ, YAxis)`-like action on target (probability match); control=0 is identity.
   - `RZZ(θ)` diagonal → no probability change but verify relative phase via interference (sandwich between H gates).
4. **Parametric interface checks** — for every `ParametricGate`: `setParameter(0, x)` then `getParameter()==x`; matrix actually changes; `getParameterCount()` correct (4 for `CU`).
5. **Inverse checks** — for non-self-inverse gates: build gate, capture `M`; new gate with `setInverse(true)`; assert `M_inv == M†`.
6. **Property test** (optional, ties to Section 10) — random parameters for parametric gates must remain unitary.

Reference existing simulator test patterns by reading any current test that drives `Program` + `SimpleQuantumExecutionEnvironment` (search `algorithm/`-adjacent tests) and mirror their structure (per global instruction: mirror patterns from existing test classes in the same module; re-read controller/service signatures first).

---

## 6. Risks & Edge Cases

1. **Basis / qubit-ordering convention.** The simulator's interpretation of which listed qubit is the high-order bit determines where controlled blocks sit (rows {2,3} vs {1,3}). `Cnot`'s matrix (X-action on rows 2,3) is the ground truth — author every controlled gate (`CU`, `Fredkin`, `C3X`) to match `Cnot`'s convention and confirm with known-state tests **before** finalising. This is the single biggest correctness risk.
2. **Global phase.** `SX·SX = X`, `T^8 = I`, and `CU`'s γ introduce global phases that are physically irrelevant but break naive amplitude equality. Tests must compare **probabilities** (or factor out global phase), not raw amplitudes.
3. **`final` field removal in retrofits.** `Rotation.thetav` and `R.expv` become mutable. Ensure no other code relies on their `final`-ness (grep usages). Keep the change minimal — only de-`final` and extract `buildMatrix()`.
4. **`setInverse` mutates shared matrix in place.** Existing pattern (`SingleQubitMatrixGate`, `Rotation`, `R`) reassigns the `matrix` field via `conjugateTranspose`. Calling `setInverse(true)` twice double-inverts (back to original). Document this; tests should not assume idempotency. New parametric gates: if both `setInverse` and `setParameter` are called, `buildMatrix()` resets to the non-inverted form — acceptable, but note the ordering caveat in Javadoc.
5. **`C3X` / 4-qubit support.** The dense simulator (`Computations.java`) may not handle 4-qubit gate matrices if it special-cases 1/2/3-qubit gates. Verify first; if unsupported, **defer `C3X`** and ship it as a `BlockGate` decomposition in a later (Section 4) plan instead of a raw 16×16 matrix. `RC3X` (3-qubit) is unaffected.
6. **Redundant fixed vs parametric classes.** `S/T` duplicate `P(π/2)/P(π/4)`. Intentional, mirrors `Cz` vs `Cr`. Keep both; do not "refactor" `S` to delegate to `P` (would change captions/identity used by visualisation in StrangeFX).
7. **`Deutsch` parameter range.** Only θ values making the block unitary are valid; the given `[[i cosθ, sinθ],[sinθ, i cosθ]]` is unitary for all real θ — verify via the unitarity test rather than restricting input.
8. **License header drift.** Newer files (`Rotation*.java`) lack the header; do not copy that omission — include the full BSD header on all new files to match the dominant convention and avoid license-audit gaps.

---

## 7. Suggested Sequencing

| Step | Items | Why first |
|---|---|---|
| 1 | `ParametricGate` interface (3.0) | Unblocks every parametric retrofit/new gate; pure addition. |
| 2 | Phase family `S,T,Sdg,Tdg` + `P` (3.1); `SX,SXdg` (3.2) | Simplest, highest value (universal set, IBM native); no base-class work. |
| 3 | Retrofit `Rotation*, R, Cr` to `ParametricGate` (3.6) | Validates the interface against real code early. |
| 4 | Two-qubit fixed: `ISwap, SqrtISwap, DCX, ECR` (3.3, 3.4) | No base-class work; exercises convention vs `Cnot`. |
| 5 | Two-qubit parametric: `RXX, RYY, RZZ` (3.5) | Builds on validated parametric pattern. |
| 6 | `CU` (3.9) | Highest convention risk; do after ordering is nailed by tests. |
| 7 | Three-qubit: `Fredkin` (3.7), `Deutsch` (3.8), `RC3X` (3.10a) | Reuse `ThreeQubitGate`; follow `Toffoli` template. |
| 8 | `Gate` factories (4.1) for all shipped gates | Wire up public API incrementally as each gate lands. |
| 9 | `FourQubitGate` + `C3X` (4.3, 3.10b) | Only after simulator 4-qubit support is verified; otherwise defer. |
| 10 | Test suite (Section 5) | Authored alongside each step; full unitarity sweep at the end. |

Each step is independently mergeable and leaves the build green.
