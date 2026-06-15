package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Block;
import com.orion.quantumcomputing.BlockGate;
import com.orion.quantumcomputing.Complex;
import com.orion.quantumcomputing.QuantumStep;
import java.util.HashMap;

public class AddInteger extends BlockGate<AddInteger>
{
    static HashMap<Integer, Block> cache = new HashMap<>();
    Block block;


    /**
     * Add the qubit in the x register and the y register, result is in x
     *
     * @param x0 start idx x register
     * @param x1 end idx x register
     * @param num the integer to be added, (y_m.. y_0)
     * x_0 ----- y_0 + x_0
     * x_1 ----- y+1 + x_1
     */
    public AddInteger(int x0, int x1, int num)
    {
        super();
        setIndex(x0);
        x1 = x1 - x0;
        x0 = 0;
        int hash = 1000000 * x0 + 10000 * x1 + num;
        this.block = cache.get(hash);
        if(this.block == null)
        {
            this.block = createBlock(x0, x1, num);
        }
        setBlock(block);
    }


    public Block createBlock(int x0, int x1, int num)
    {
        boolean old = false;
        int m = x1 - x0 + 1;
        Block answer = new Block("AddInteger ", m);
        answer.addStep(new QuantumStep(new Fourier(m, 0)));
        QuantumStep pstep = new QuantumStep();
        for(int i = 0; i < m; i++)
        {
            Complex[][] mat = Complex.identityMatrix(2);
            for(int j = 0; j < m - i; j++)
            {
                int cr0 = m - j - i - 1;
                if((num >> cr0 & 1) == 1)
                {
                    mat = Complex.mmul(mat, new R(2, 1 + j, i).getMatrix());
                    if(old)
                    {
                        QuantumStep s = new QuantumStep(new R(2, 1 + j, i));
                        answer.addStep(s);
                    }
                }
            }
            if(!old)
            {
                pstep.addGate(new SingleQubitMatrixGate(i, mat));
            }
        }
        if(!old)
        {
            answer.addStep(pstep);
        }
        answer.addStep(new QuantumStep(new InvFourier(m, 0)));
        return answer;
    }


    @Override
    public String getCaption()
    {
        return "A\nD\nD\nI";
    }
}
