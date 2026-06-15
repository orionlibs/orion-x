package com.orion.quantumcomputing.gate;

public class AddIntegerModulus extends BlockGate<AddIntegerModulus>
{
    Block block;


    /**
     * Add integer a to the qubit in the x register mod N, result is in x
     *
     * @param x0 start idx x register
     * @param a a int
     * @param N a int
     */
    public AddIntegerModulus(int x0, int x1, int a, int N)
    {
        super();
        int n = x1 - x0 + 1;
        if(N >= (1 << n))
        {
            throw new IllegalArgumentException("AddIntegerModules with n = " + n + " but modulus is bigger than max: " + N);
        }
        setIndex(x0);
        x1 = x1 - x0;
        x0 = 0;
        this.block = createBlock(x0, x1, a, N);
        setBlock(block);
    }


    public Block createBlock(int x0, int x1, int a, int N)
    {
        Block answer = new Block("AddIntegerModulus", x1 - x0 + 2);
        int n = x1 - x0;
        int dim = n + 1;
        org.redfx.strange.gate.AddInteger add = new org.redfx.strange.gate.AddInteger(x0, x1, a);
        answer.addStep(new QuantumStep(add));
        org.redfx.strange.gate.AddInteger min = new org.redfx.strange.gate.AddInteger(x0, x1, N).inverse();
        answer.addStep(new QuantumStep(min));
        answer.addStep(new QuantumStep(new Cnot(x1, dim)));
        org.redfx.strange.gate.AddInteger addN = new org.redfx.strange.gate.AddInteger(x0, x1, N);
        ControlledBlockGate cbg = new ControlledBlockGate(addN, x0, dim);
        answer.addStep(new QuantumStep(cbg));
        org.redfx.strange.gate.AddInteger add2 = new org.redfx.strange.gate.AddInteger(x0, x1, a).inverse();
        answer.addStep(new QuantumStep(add2));
        answer.addStep(new QuantumStep(new X(dim - 1)));
        answer.addStep(new QuantumStep(new Cnot(x1, dim)));
        answer.addStep(new QuantumStep(new X(dim - 1)));
        org.redfx.strange.gate.AddInteger add3 = new AddInteger(x0, x1, a);
        answer.addStep(new QuantumStep(add3));
        return answer;
    }
}
