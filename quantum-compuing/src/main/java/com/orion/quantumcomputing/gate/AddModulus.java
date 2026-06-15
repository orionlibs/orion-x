package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Block;
import com.orion.quantumcomputing.BlockGate;
import com.orion.quantumcomputing.ControlledBlockGate;
import com.orion.quantumcomputing.QuantumStep;
import java.util.HashMap;

public class AddModulus extends BlockGate<AddModulus>
{
    static HashMap<Integer, Block> cache = new HashMap<>();
    Block block;


    /**
     * Add the qubit in the x register and the y register mod N, result is in x
     *
     * @param x0 start idx x register
     * @param x1 end idx x register
     * @param y0 start idx y register
     * @param y1 end idx y register
     * @param N
     * x_0 ----- y_0 + x_0
     * x_1 ----- y+1 + x_1
     * y_0 ----- y_0
     * y_1 ----- y_1
     * ANC(0)--- ANC(0)
     * the qubit following y_1 should be 0 (and will be 0 after this gate)
     * qubit at x_1 should be 0 and qubit at y_1 should be 0 (overflow)
     */
    public AddModulus(int x0, int x1, int y0, int y1, int N)
    {
        super();
        setIndex(x0);
        y1 = y1 - x0;
        y0 = y0 - x0;
        x1 = x1 - x0;
        x0 = 0;
        assert (y0 == x1 + 1);
        int hash = 1000000 * x0 + 10000 * x1 + 100 * y0 + 10 * y1 + N;
        this.block = cache.get(hash);
        if(this.block == null)
        {
            this.block = createBlock(x0, x1, y0, y1, N);
        }
        else
        {
        }
        setBlock(block);
    }


    public Block createBlock(int x0, int x1, int y0, int y1, int N)
    {
        Block answer = new Block("AddModulus", y1 - x0 + 2);
        int n = x1 - x0;
        int dim = 2 * (n + 1) + 1;
        Add add = new Add(x0, x1, y0, y1);
        answer.addStep(new QuantumStep(add));
        AddInteger min = new AddInteger(x0, x1, N).inverse();
        answer.addStep(new QuantumStep(min));
        answer.addStep(new QuantumStep(new Cnot(x1, dim - 1)));
        AddInteger addN = new AddInteger(x0, x1, N);
        ControlledBlockGate cbg = new ControlledBlockGate(addN, x0, dim - 1);
        answer.addStep(new QuantumStep(cbg));
        Add add2 = new Add(x0, x1, y0, y1).inverse();
        answer.addStep(new QuantumStep(add2));
        answer.addStep(new QuantumStep(new X(dim - 1)));
        Block block = new Block(1);
        block.addStep(new QuantumStep(new X(0)));
        ControlledBlockGate cbg2 = new ControlledBlockGate(block, dim - 1, x1);
        answer.addStep(new QuantumStep(cbg2));
        Add add3 = new Add(x0, x1, y0, y1);
        answer.addStep(new QuantumStep(add3));
        return answer;
    }


    @Override
    public String getCaption()
    {
        return "A\nD\nD\n";
    }
}
