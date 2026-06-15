# Section 11 — Visualisation: Implementation Plan

## 1. Overview & Goals

Give the core `org.redfx.strange` library model-level rendering and export, so a
`Program` can be inspected, embedded in papers, fed to web renderers, and have its
results exported, **without** requiring the JavaFX `StrangeFX` module.

Deliverables:

1. **Render model** — an intermediate, backend-agnostic layout (`CircuitDiagram`)
   describing each gate as a placed cell on a qubit-row × QuantumStep-column grid, plus
   connector spans. ASCII, LaTeX, and SVG/HTML all consume this single model so
   layout logic is written once.
2. **ASCII backend** — `Program.toAsciiCircuit()` returning a `String`.
3. **LaTeX / quantikz backend** — `Program.toQuantikz()` for papers.
4. **SVG/HTML backend** — `Program.toSvg()` / expose the model for external renderers.
5. **State-vector histogram export** — JSON/CSV of per-basis-state probabilities
   from `Result`.
6. **Bloch-sphere data export** — `(x,y,z)` per single-qubit state (depends on
   Section 8, which computes Bloch coordinates).

Design constraints:

- Pure model-level Java, no JavaFX, no third-party deps (keeps it usable from the
  core jar and GraalVM native-image). JSON written by a tiny hand-rolled writer or
  via a small interface so callers can swap in Jackson.
- Read-only over the existing API; the only proposed source changes are **additive
  accessors** (Section 7) needed because the current `Gate` API does not expose
  control-vs-target roles.

## 2. Current API available for rendering (real accessors)

From the files read:

**`Program`** (`Program.java`):
- `int getNumberQubits()`
- `List<Step> getSteps()`
- `double[] getInitialAlphas()` — initial qubit alphas (for `|0>`/`|1>` start labels)
- `Result getResult()`
- existing `void printInfo()` — prints `numberQubits`, QuantumStep count, and
  `step.getGates()` per QuantumStep. This is the only current "renderer"; we keep it and
  add the new methods alongside.

**`Step`** (`Step.java`):
- `List<Gate> getGates()` (unmodifiable)
- `Step.Type getType()` — `NORMAL` / `PSEUDO` / `PROBABILITY`. PSEUDO QuantumSteps "do not
  alter the circuit" and are used for visualization; the render model keeps them as
  spacer columns but they carry no compute gate.
- `String getName()`, `int getIndex()`, `boolean isInformal()`

**`Gate`** (`Gate.java`) — the interface every gate implements:
- `String getCaption()` — short glyph text. Real values seen:
  - `Hadamard` → `"H"`, `X` → `"X"`, `Measurement` → `"M"`, `Cnot` → `"Cnot"`,
    `Swap` → `"S"` (⚠ collides with a future phase-S gate — see Risks),
    `Toffoli` → `"CCnot"`.
  - Base classes `SingleQubitGate`/`TwoQubitGate`/`ThreeQubitGate` default
    `getCaption()` to `getName()` (the FQCN) when a subclass does not override —
    so the renderer must tolerate long/ugly captions.
- `String getName()` — defaults to `getClass().getName()` (FQCN).
- `String getGroup()` — `"SingleQubit"`, `"TwoQubit"`, `"ThreeQubit"`, etc. Usable
  as a coarse classifier.
- `int getMainQubitIndex()`
- `List<Integer> getAffectedQubitIndexes()`
- `int getHighestAffectedQubitIndex()`
- `int getSize()` — number of qubits acted on (1/2/3).
- `Complex[][] getMatrix()` — last-resort identity for gate classification.

**Per-gate index semantics (verified):**
- `SingleQubitGate`: `getAffectedQubitIndexes()` = `[idx]`; `getMainQubitIndex()` = idx.
- `TwoQubitGate` (Cnot, Cz, Swap): fields `first`, `second`.
  - `getMainQubitIndex()` → `first`
  - `getSecondQubitIndex()` → `second`  *(public, only on `TwoQubitGate`, not on the
    `Gate` interface — renderer must `instanceof`-check)*
  - `getAffectedQubitIndexes()` → `[first, second]`
  - For **Cnot**, `Program.ensureMeasuresafe` treats `getSecondQubitIndex()` as the
    qubit that gets superpositioned → **`first` = control, `second` = target**.
  - For **Cz** the roles are symmetric (control dot on both).
  - For **Swap** both are swap endpoints (× on both).
- `ThreeQubitGate` (Toffoli): public `getMainQubit()`/`getSecondQubit()`/
  `getThirdQubit()` (note: `getMainQubit`, not `...Index`). For Toffoli,
  first+second = controls, third = target.

**`Result`** (`Result.java`) — histogram source:
- `Complex[] getProbability()` — the **full state vector**, length `2^n`. Basis
  state `j` has amplitude `getProbability()[j]`; probability = `.abssqr()`.
  Bit order: `Result.calculateQubitStatesFromVector` uses `qubit i` ↔ bit `i`
  (`p1 = j/(1<<i); bit = p1 % 2`), i.e. **qubit 0 is the least-significant bit** of
  basis index `j`. The histogram exporter must document and follow this convention.
- `Complex[] getIntermediateProbability(int QuantumStep)` — state vector after a QuantumStep.
- `Qubit[] getQubits()`, `Qubit.getProbability()` — per-qubit P(measuring 1).
- existing `Result.printInfo()` prints `probability[i].abssqr()` per index — the
  current ad-hoc histogram; we generalize it.

**Critical gap:** nothing on the `Gate` interface says "this index is a control
dot vs a target box vs a swap end." We infer it today by `instanceof Cnot` /
`Swap` / `Toffoli` plus the `first/second/third` ordering. Section 7 below proposes
optional additive accessors so the renderer is not a growing `instanceof` cascade.

## 3. Detailed work items (ordered)

New package: `org.redfx.strange.viz`. New directory: `viz/` at repo root (flat
layout matches the existing structure where `gate/`, `local/`, `algorithm/` sit
beside the core files).

### 3.0 — Render model & layout engine  (`viz/CircuitDiagram.java`, `viz/CellKind.java`)

The single intermediate representation every backend consumes.

```java
package org.redfx.strange.viz;

public enum CellKind {
    BOX,        // single-qubit boxed gate: H, X, Y, Z, Rx... , Measurement
    CONTROL,    // filled control dot (Cnot/Cz/Toffoli control)
    TARGET_X,   // ⊕ CNOT/Toffoli target
    SWAP_END,   // × swap endpoint
    WIRE,       // pass-through (no gate this row this column)
    PSEUDO      // spacer for PSEUDO/PROBABILITY QuantumSteps
}

public final class GateCell {
    public final int row;          // qubit index (0 = top wire)
    public final int col;          // QuantumStep column
    public final CellKind kind;
    public final String label;     // caption text, e.g. "H", "Rx(π/2)"; "" for connectors
    public final int spanTopRow;   // min row of the multi-qubit gate it belongs to
    public final int spanBottomRow;// max row; used to draw vertical connectors
    public final boolean isMultiQubitMember;
    public final java.util.List<Integer> partnerRows; // other rows in same gate
    // ctor + getters
}

public final class CircuitDiagram {
    public final int numQubits;
    public final int numColumns;
    public final java.util.List<GateCell> cells;     // every placed cell
    public final double[] initialAlphas;             // for |0>/|1> labels
    public GateCell cellAt(int row, int col) { ... }
    // getters
}
```

**Builder: `viz/DiagramBuilder.java`**

```java
public final class DiagramBuilder {
    public static CircuitDiagram build(Program p) { ... }
}
```

Algorithm (`build`):

1. `n = p.getNumberQubits()`. Iterate `p.getSteps()`; one **column per QuantumStep**
   (column index = position in the QuantumStep list; do not rely on `step.getIndex()`
   only, but assert it matches). PSEUDO/PROBABILITY QuantumSteps still get a column with
   `PSEUDO` cells on every row (keeps alignment with `StrangeFX` semantics).
2. For each `Step`, start every row as a `WIRE` cell, then overwrite rows touched
   by its gates. (Step invariant guarantees ≤1 gate per qubit per QuantumStep, see
   `Step.verifyUnique`.)
3. For each `Gate g` in the QuantumStep, classify via `classify(g)`:
   - **single-qubit** (`g.getSize()==1`): `BOX` at `g.getMainQubitIndex()`,
     `label = captionOf(g)`. `Measurement` → `BOX` with label `"M"` (a backend may
     render it as a meter glyph).
   - **two-qubit**: read `main = g.getMainQubitIndex()`,
     `second = ((TwoQubitGate)g).getSecondQubitIndex()`.
     - `Cnot`: `CONTROL` at `main`, `TARGET_X` at `second`.
     - `Cz`: `CONTROL` at both rows (or `CONTROL` at main, `BOX "Z"` at second —
       pick `CONTROL`+`CONTROL` with a `Z` connector label to match quantikz `\ctrl`).
     - `Swap`: `SWAP_END` at both rows.
     - unknown two-qubit: `BOX` spanning both rows labelled `captionOf(g)`.
     Set `spanTopRow=min`, `spanBottomRow=max`, `partnerRows`, `isMultiQubitMember=true`
     on each member cell.
   - **three-qubit** (`Toffoli`): `getMainQubit()`/`getSecondQubit()` →
     `CONTROL`, `getThirdQubit()` → `TARGET_X`. Generic 3-qubit → `BOX` span.
4. Vertical-connector spans are *derived*, not stored as separate cells: any column
   whose multi-qubit cells have `spanTopRow < spanBottomRow` tells a backend to draw
   a vertical line between those rows. Rows strictly between top and bottom that are
   not themselves members are "crossed" wires (the connector passes through them).

`classify(g)` decision order (kept small, documented): `instanceof Cnot`,
`instanceof Cz`, `instanceof Swap`, `instanceof Toffoli`, `instanceof Measurement`,
then fall back on `g.getSize()`. If Section 7 accessors land, replace the
`instanceof` chain with `g.getRenderRole(idx)`.

`captionOf(g)`: return `g.getCaption()`; if it equals or starts with the FQCN
(`g.getName()` / contains a `.`), fall back to a short class-simple-name so the
default `SingleQubitGate.getCaption()==getName()` case does not dump
`Foo` into the diagram.

### 3.1 — ASCII backend  (`viz/AsciiRenderer.java`; method `Program.toAsciiCircuit()`)

```java
public final class AsciiRenderer {
    public static String render(CircuitDiagram d) { ... }
}
```

Add to `Program`:

```java
public String toAsciiCircuit() { return AsciiRenderer.render(DiagramBuilder.build(this)); }
```

Layout: each qubit becomes **3 text rows** (top padding / wire / bottom padding) so
boxes have borders and vertical connectors have somewhere to live. Each column has a
fixed inner width = `max(label length over column, 1)` padded to odd width so the
wire `─` centers.

Glyph rules:
- `WIRE`        → `───`
- `BOX "H"`     → `┤ H ├` on the wire row, `┌─┐`/`└─┘` corners on pad rows
- `CONTROL`     → `─●─`
- `TARGET_X`    → `─⊕─` (CNOT/Toffoli target)
- `SWAP_END`    → `─╳─` (or `─X─` in ASCII-only mode)
- vertical connector between two member rows → `│` placed in the pad rows of every
  row strictly between `spanTopRow` and `spanBottomRow`, and in the relevant pad row
  of the member rows.
- left margin: `q0 : |0>─` (use `initialAlphas[i]` to choose `|0>` vs `|1>` — alpha
  `1.0` ⇒ `|0>`, `0.0` ⇒ `|1>`, otherwise `|ψ>`).

Provide an `AsciiRenderer.Charset` toggle: `UNICODE` (default, box-drawing) and
`ASCII` (`+-|`, `*` control, `X` target, `x` swap) for terminals/golden files that
need 7-bit output.

**Sample output — Bell state**
`new Program(2, new QuantumStep(Gate.hadamard(0)), new QuantumStep(Gate.cnot(0,1)), new QuantumStep(Gate.measurement(0), Gate.measurement(1)))`:

```
q0 : |0>──┤ H ├────●────┤ M ├─
          └───┘    │    └───┘
                   │
q1 : |0>───────────⊕────┤ M ├─
                        └───┘
```

(ASCII charset variant:)

```
q0 : |0>--[ H ]----*----[ M ]-
                   |
q1 : |0>-----------X----[ M ]-
```

### 3.2 — LaTeX / quantikz backend  (`viz/QuantikzRenderer.java`; `Program.toQuantikz()`)

```java
public final class QuantikzRenderer {
    public static String render(CircuitDiagram d) { ... } // returns full tikzcd block
}
```

Emit a `quantikz` environment (CTAN package; the standard for circuits in papers).
One LaTeX row per qubit, columns separated by `&`, rows by `\\`.

Macro mapping (per `CellKind` / label):
- `BOX label`   → `\gate{label}`  (escape `label`; map `π`→`\pi`, etc.)
- `WIRE`        → `\qw`
- `CONTROL` with target `k` rows away → `\ctrl{k}` (k = signed row offset to the
  target/partner; quantikz draws the vertical line + dot)
- `TARGET_X`    → `\targ{}`
- `SWAP_END`    → `\swap{k}` on the upper end, `\targX{}` on the lower (quantikz
  convention), or `\swap{k}` paired with `\qw`.
- `Measurement` → `\meter{}`
- `PSEUDO`      → `\qw`
- lead-in column: `\lstick{$\ket{0}$}` (or `\ket{1}`/`\ket{\psi}` from
  `initialAlphas`).

Control-offset note: quantikz `\ctrl{k}` needs the **row delta** to the partner, not
absolute index — compute `k = partnerRow - controlRow` (sign matters). The render
model already stores `partnerRows`, so this is a subtraction.

Output is a `String`; wrap in `\begin{quantikz} ... \end{quantikz}`. Provide
`render(d, boolean standalone)` to optionally emit a full compilable `\documentclass`
+ `\usepackage{quantikz}` wrapper for quick previews.

Bell-state expected output:
```
\begin{quantikz}
\lstick{$\ket{0}$} & \gate{H} & \ctrl{1} & \meter{} \\
\lstick{$\ket{0}$} & \qw      & \targ{}  & \meter{}
\end{quantikz}
```

### 3.3 — SVG / HTML backend  (`viz/SvgRenderer.java`; `Program.toSvg()`)

```java
public final class SvgRenderer {
    public static String render(CircuitDiagram d) { ... }              // standalone <svg>
    public static String renderHtml(CircuitDiagram d) { ... }          // <html> wrapper
}
```

- Fixed geometry constants: `colWidth=60`, `rowHeight=50`, `boxW=36`, `boxH=30`,
  margins. `x(col) = leftMargin + col*colWidth`, `y(row) = topMargin + row*rowHeight`.
- Draw horizontal wire `<line>` per row spanning all columns first (so gates sit on
  top).
- Per cell: `BOX` → `<rect>` + centered `<text>`; `CONTROL` → `<circle r=4 fill>`;
  `TARGET_X` → circle + cross; `SWAP_END` → two crossing `<line>`s; vertical
  connector → `<line>` from `y(spanTopRow)` to `y(spanBottomRow)` at `x(col)`.
- Pure string concatenation → no XML/DOM dependency; safe for native-image.
- **Expose the model** explicitly: `DiagramBuilder.build(program)` is public so an
  external JS/StrangeFX renderer can serialize `CircuitDiagram` (add a
  `CircuitDiagram.toJson()` mirroring the histogram JSON writer) and render in the
  browser. This satisfies the idea-file bullet "expose the model here."

### 3.4 — State-vector histogram export  (`viz/HistogramExport.java`)

```java
public final class HistogramExport {
    public static String toCsv(Result r) { ... }
    public static String toJson(Result r) { ... }
    public static String toCsv(Complex[] stateVector) { ... } // for intermediate QuantumSteps
}
```

Source: `Result.getProbability()` → `Complex[]` of length `2^n`. For each basis
index `j`:
- `basis` label = `j` formatted as an `n`-bit binary string. **Bit convention:**
  qubit 0 is the LSB (matches `Result.calculateQubitStatesFromVector`). Document
  this; offer a `boolean qubit0IsMsb` flag to reverse for users expecting Qiskit-style
  ordering.
- `probability = stateVector[j].abssqr()`
- `amplitudeReal`, `amplitudeImag` from `Complex` (the existing `Complex` record/class
  exposes real/imag; confirm accessor names when implementing — `Complex.java` has
  `abssqr()` used here).

CSV:
```
basis,index,probability,amplitude_real,amplitude_imag
00,0,0.5,0.7071,0.0
01,1,0.0,0.0,0.0
10,2,0.0,0.0,0.0
11,3,0.5,0.7071,0.0
```

JSON:
```json
{"numQubits":2,"qubit0":"lsb","states":[
  {"basis":"00","index":0,"probability":0.5,"re":0.70710678,"im":0.0},
  {"basis":"11","index":3,"probability":0.5,"re":0.70710678,"im":0.0}
]}
```

Optionally filter zero-probability rows (`includeZeros=false`) for large `n`.
JSON is written by a small private `JsonWriter` (StringBuilder, proper escaping) so
no Jackson/Gson dependency is forced on the core jar; if Section 10 serialisation
adds a JSON layer later, swap to it.

### 3.5 — Bloch-sphere data export  (`viz/BlochExport.java`)

Single-qubit Bloch vector `(x,y,z)` per qubit. This **depends on Section 8**, which
adds the actual `(x,y,z)` computation from the reduced single-qubit density matrix.

- If Section 8 provides e.g. `BlochSphere.coordinates(Result, int qubit)` or a method
  on `Qubit`/`Result`, `BlochExport` just iterates qubits and formats CSV/JSON.
- Until Section 8 lands, expose a stub that computes Bloch coords only for
  **product (unentangled) single-qubit** states from `Qubit.getProbability()`
  (gives `z = 1 - 2*P(1)` only; `x,y` require phase, which needs Section 8's density
  matrix). Mark this clearly and gate the full version behind Section 8.

```java
public final class BlochExport {
    public static String toCsv(Result r) { ... }   // qubit,x,y,z
    public static String toJson(Result r) { ... }
}
```

CSV:
```
qubit,x,y,z
0,0.0,0.0,1.0
```

## 4. Proposed additive source changes (the "insufficient accessors" note)

The render model currently classifies multi-qubit gates with an `instanceof` chain
because the `Gate` interface exposes **no role information**. Recommended *optional,
additive* changes (do not break existing behaviour):

1. On `Gate` (interface) add a default method:
   ```java
   default RenderRole getRenderRole(int qubitIndex) { return RenderRole.BOX; }
   ```
   with `enum RenderRole { BOX, CONTROL, TARGET, SWAP }`. Override in `Cnot`
   (control on `first`, target on `second`), `Cz`, `Swap`, `Toffoli`. This collapses
   `classify()` to a per-affected-index lookup and lets future gates self-describe.
2. Promote `getSecondQubitIndex()` (currently only on `TwoQubitGate`) and the
   `ThreeQubitGate` `getSecondQubit()/getThirdQubit()` to a consistent
   `getControlQubits()` / `getTargetQubits()` pair, OR keep the render model's
   `instanceof` shim if minimal source change is preferred. **Default plan: keep
   render-model shim, ship `getRenderRole` as the clean follow-up** so Section 11 does
   not block on touching every gate.
3. Fix the `Swap` caption collision: `Swap.getCaption()` returns `"S"`, which will
   clash visually with a future phase-S gate (Section 1). The renderer should special-
   case `Swap` via `RenderRole.SWAP` rather than the caption, so the collision is
   cosmetic only — note it for whoever adds the S gate.

These are flagged but **not required** for a first working version: the render model
can ship using `instanceof` today.

## 5. Cross-section dependencies

- **Section 8 (Quantum Information & Tomography)** — owns Bloch `(x,y,z)`
  computation from the single-qubit reduced density matrix. `BlochExport` (§3.5) is a
  thin formatter over it. Sequence Section 8's coordinate method before the full
  Bloch export; ship the product-state stub meanwhile.
- **Section 10 (Serialisation)** — if a JSON layer is added there, `HistogramExport`
  and `CircuitDiagram.toJson()` should reuse it instead of the local `JsonWriter`.
- **Section 1 (Gate library)** — new gates (S, T, iSWAP, Fredkin...) must set a
  sensible `getCaption()` and, ideally, `getRenderRole()`; otherwise they render as
  FQCN boxes (the `captionOf` fallback keeps this readable but ugly).

## 6. Testing strategy

Golden-file comparison on a small set of canonical circuits, primarily the **Bell
state** plus a Toffoli and a Swap circuit.

- `viz/AsciiRendererTest` — build the Bell `Program`, call `toAsciiCircuit()` (ASCII
  charset for stable bytes), assert equals a checked-in golden string. One test for
  CONTROL/TARGET vertical connector (Cnot), one for SWAP (`Swap`), one for Toffoli
  (two controls + target spanning 3 rows), one for `Measurement` boxes.
- `viz/QuantikzRendererTest` — assert the Bell circuit produces the exact
  `\gate{H} & \ctrl{1} ... \targ{}` golden block; verify `\ctrl{k}` offset sign for
  a control **below** vs **above** its target.
- `viz/HistogramExportTest` — run the Bell `Program` through
  `SimpleQuantumExecutionEnvironment` (in `local/`), then assert
  `toCsv(result)`/`toJson(result)` yields `00→0.5`, `11→0.5`, `01/10→0`. Asserts the
  LSB qubit-ordering convention explicitly (so a future reorder is caught).
- `viz/SvgRendererTest` — smoke test: output contains `<svg`, the right number of
  `<rect>` (boxes) and `<circle>` (controls), and is well-formed enough to parse.
- `viz/BlochExportTest` — `|0>` → `(0,0,1)`, `|1>` (init alpha 0) → `(0,0,-1)`,
  `H|0>` → `(1,0,0)` *(this last one requires Section 8; gate behind it)*.
- Layout unit test on `DiagramBuilder`: assert `numColumns == QuantumSteps.size()`, cell
  kinds at known `(row,col)` for the Bell circuit, and `partnerRows`/span correctness
  for the Cnot.

Keep golden files next to the tests; ASCII charset = `ASCII` mode so no Unicode
normalization issues in CI.

## 7. Risks

- **Caption quality / FQCN leakage**: default `getCaption()` returns the class name
  for gates that don't override it. Mitigated by `captionOf` fallback, but output is
  ugly for unlabeled gates. Risk: visual regressions when new gates are added.
- **Control vs target ambiguity**: inferred from `instanceof` + `first/second`
  ordering. If a new controlled gate uses a different field order, it renders wrong.
  Mitigated by the `getRenderRole` follow-up.
- **Swap caption `"S"` collision** with a future phase-S gate (Section 1). Renderer
  must key off role, not caption.
- **Qubit bit-ordering**: histogram LSB-of-`j` = qubit 0. Users from Qiskit expect
  the opposite; wrong assumption silently mislabels basis states. Mitigated by an
  explicit flag and a dedicated test asserting the convention.
- **PSEUDO/PROBABILITY QuantumSteps**: must become spacer columns, not compute gates, or
  column alignment drifts. Covered by treating them as `PSEUDO` cells.
- **Wide / nested gates** (`BlockGate`, `ControlledBlockGate`, `Oracle`, QFT,
  arithmetic gates): these can span many qubits and may decompose. v1 renders them as
  a single labelled box spanning `getMainQubitIndex()..getHighestAffectedQubitIndex()`;
  decomposed rendering (using the deprecated `getDecomposedSteps`) is out of scope.
- **Unicode in ASCII output**: box-drawing chars break some terminals/CI logs — hence
  the `ASCII` charset mode is the default for tests.

## 8. Suggested sequencing

1. `CellKind`, `GateCell`, `CircuitDiagram`, `DiagramBuilder` (render model + layout).
   Unblocks everything; testable on its own.
2. `AsciiRenderer` + `Program.toAsciiCircuit()` + golden tests (highest user value,
   no external deps).
3. `HistogramExport` (CSV/JSON) + tests — independent of the diagram path, can be done
   in parallel with QuantumStep 2.
4. `QuantikzRenderer` + `Program.toQuantikz()` + golden tests.
5. `SvgRenderer` + `Program.toSvg()` + `CircuitDiagram.toJson()` (expose model).
6. `getRenderRole` additive accessors on gates; refactor `classify()` to use them.
7. Bloch export — **after** Section 8's coordinate computation lands (ship product-
   state stub earlier if useful).
