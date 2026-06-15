package com.orion.quantumcomputing;

import java.util.List;

public interface QuantumGate
{
    static QuantumGate cnot(int a, int b)
    {
        return new Cnot(a, b);
    }


    static QuantumGate cz(int a, int b)
    {
        return new Cz(a, b);
    }


    static QuantumGate hadamard(int idx)
    {
        return new Hadamard(idx);
    }


    static QuantumGate identity(int idx)
    {
        return new Identity(idx);
    }


    static QuantumGate measurement(int idx)
    {
        return new QuantumMeasurement(idx);
    }


    static QuantumGate oracle(int idx)
    {
        return new Oracle(idx);
    }


    static QuantumGate oracle(Complex[][] matrix)
    {
        return new Oracle(matrix);
    }


    static QuantumGate permutation(int a, int b, int n)
    {
        return new PermutationGate(a, b, n);
    }


    static QuantumGate probability(int idx)
    {
        return new ProbabilitiesGate(idx);
    }


    static QuantumGate swap(int a, int b)
    {
        return new Swap(a, b);
    }


    static QuantumGate toffoli(int a, int b, int c)
    {
        return new Toffoli(a, b, c);
    }


    static QuantumGate x(int idx)
    {
        return new X(idx);
    }


    static QuantumGate y(int idx)
    {
        return new Y(idx);
    }


    static QuantumGate z(int idx)
    {
        return new Z(idx);
    }


    static QuantumGate rotation(double theta, Rotation.Axes axis, int idx)
    {
        return new Rotation(theta, axis, idx);
    }


    static QuantumGate rotationX(double theta, int idx)
    {
        return new RotationX(theta, idx);
    }


    static QuantumGate rotationY(double theta, int idx)
    {
        return new RotationY(theta, idx);
    }


    static QuantumGate rotationZ(double theta, int idx)
    {
        return new RotationZ(theta, idx);
    }


    public int getMainQubitIndex();


    public void setMainQubitIndex(int idx);


    public void setAdditionalQubit(int idx, int cnt);


    public List<Integer> getAffectedQubitIndexes();


    public int getHighestAffectedQubitIndex();


    public String getCaption();


    public String getName();


    public String getGroup();


    public Complex[][] getMatrix();


    public int getSize();


    default public Complex[][] getMatrix(QuantumExecutionEnvironment qee)
    {
        return getMatrix();
    }


    default public boolean hasOptimization()
    {
        return false;
    }


    default public Complex[] applyOptimize(Complex[] v)
    {
        return null;
    }


    void setInverse(boolean inv);
}