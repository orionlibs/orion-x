package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Block;
import com.orion.quantumcomputing.BlockGate;
import com.orion.quantumcomputing.QuantumStep;
import java.util.HashMap;

public class Add extends BlockGate<Add>
{
    static HashMap<Integer, Block> cache = new HashMap<>();
    Block block;


    /**
     * Add the qubit in the x register and the y register, result is in x
     *
     * @param x0 start idx x register
     * @param x1 end idx x register
     * @param y0 start idx y register
     * @param y1 end idx y register
     * x_0 ----- y_0 + x_0
     * x_1 ----- y+1 + x_1
     * y_0 ----- y_0
     * y_1 ----- y_1
     */
    public Add(int x0, int x1, int y0, int y1)
    {
        super();
        this.setIndex(x0);
        int hash = 1000000 * x0 + 10000 * x1 + 100 * y0 + y1;
        this.block = cache.get(hash);
        if(this.block == null)
        {
            this.block = createBlock(x0, x1, y0, y1);
        }
        setBlock(block);
    }


    public Block createBlock(int x0, int x1, int y0, int y1)
    {
        Block answer = new Block("Add", y1 - x0 + 1);
        int m = x1 - x0 + 1;
        int n = y1 - y0 + 1;
        answer.addStep(new QuantumStep(new Fourier(m, 0)));
        for(int i = 0; i < m; i++)
        {
            for(int j = 0; j < m - i; j++)
            {
                int cr0 = 2 * m - j - i - 1;
                if(cr0 < m + n)
                {
                    QuantumStep s = new QuantumStep(new Cr(i, cr0, 2, 1 + j));
                    answer.addStep(s);
                }
            }
        }
        answer.addStep(new QuantumStep(new InvFourier(m, 0)));
        return answer;
    }


    @Override
    public String getCaption()
    {
        return "A\nD\nD";
    }
}
