package com.orion.quantumcomputing.gate;

import org.redfx.strange.Block;
import org.redfx.strange.BlockGate;
import org.redfx.strange.ControlledBlockGate;
import org.redfx.strange.Step;
import org.redfx.strange.gate.AddIntegerModulus;
import org.redfx.strange.gate.Swap;
import org.redfx.strange.local.Computations;

/**
 * <p>MulModulus class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class MulModulus extends BlockGate<MulModulus>
{
    Block block;
    // disable cache for now
    // static HashMap<Integer, Block> cache = new HashMap<>();


    /**
     * Multiply the qubit in the x register with an integer mul
     *
     * @param x0 start idx x register
     * @param x1 end idx x register
     * @param mul a int
     * @param mod
     * x_0 ----- x_0 * mul (n qubits in and out)
     * x_n-1 ----- x_1-1 * mul
     * y_0 ----- 0 ( n + 2 qubits needed for addintmon)
     * y_n+1 ----- 0
     */
    public MulModulus(int x0, int x1, int mul, int mod)
    {
        super();
        this.setIndex(x0);
        x1 = x1 - x0;
        x0 = 0;
        this.block = createBlock(x0, x1, mul, mod);
        setBlock(block);
    }


    /**
     * <p>createBlock.</p>
     *
     * @param y0 a int
     * @param y1 a int
     * @param mul a int
     * @param mod a int
     * @return a {@link Block} object
     */
    public Block createBlock(int y0, int y1, int mul, int mod)
    {
        int hash = 1000000 * y0 + 10000 * y1 + 100 * mul + mod;
        int x0 = y0;
        int x1 = y1 - y0;
        int size = x1 - x0 + 1;
        int n = size;
        Block answer = new Block("MulModulus", 2 * size + 2);
        for(int i = 0; i < n; i++)
        {
            int m = (mul * (1 << i)) % mod;
            org.redfx.strange.gate.AddIntegerModulus add = new org.redfx.strange.gate.AddIntegerModulus(x0, x1 + 1, m, mod);
            ControlledBlockGate cbg = new ControlledBlockGate(add, n, i);
            answer.addStep(new Step(cbg));
        }
        for(int i = x0; i < x1 + 1; i++)
        {
            answer.addStep(new Step(new Swap(i, i + size)));
        }
        int invmul = Computations.getInverseModulus(mul, mod);
        for(int i = 0; i < n; i++)
        {
            int m = (invmul * (1 << i)) % mod;
            org.redfx.strange.gate.AddIntegerModulus add = new AddIntegerModulus(x0, x1 + 1, m, mod);
            ControlledBlockGate cbg = new ControlledBlockGate(add, n, i);
            cbg.setInverse(true);
            answer.addStep(new Step(cbg));
        }
        return answer;
    }
}
