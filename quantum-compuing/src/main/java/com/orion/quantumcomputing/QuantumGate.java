package com.orion.quantumcomputing;

import com.orion.quantumcomputing.gate.Axes;
import com.orion.quantumcomputing.gate.Cnot;
import com.orion.quantumcomputing.gate.Cz;
import com.orion.quantumcomputing.gate.Hadamard;
import com.orion.quantumcomputing.gate.Identity;
import com.orion.quantumcomputing.gate.Oracle;
import com.orion.quantumcomputing.gate.PermutationGate;
import com.orion.quantumcomputing.gate.ProbabilitiesGate;
import com.orion.quantumcomputing.gate.QuantumMeasurement;
import com.orion.quantumcomputing.gate.Rotation;
import com.orion.quantumcomputing.gate.RotationX;
import com.orion.quantumcomputing.gate.RotationY;
import com.orion.quantumcomputing.gate.RotationZ;
import com.orion.quantumcomputing.gate.Swap;
import com.orion.quantumcomputing.gate.Toffoli;
import com.orion.quantumcomputing.gate.X;
import com.orion.quantumcomputing.gate.Y;
import com.orion.quantumcomputing.gate.Z;
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


    static QuantumGate rotation(double theta, Axes axis, int idx)
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


    int getMainQubitIndex();


    void setMainQubitIndex(int idx);


    void setAdditionalQubit(int idx, int cnt);


    List<Integer> getAffectedQubitIndexes();


    int getHighestAffectedQubitIndex();


    String getCaption();


    String getName();


    String getGroup();


    Complex[][] getMatrix();


    int getSize();


    default Complex[][] getMatrix(QuantumExecutor qee)
    {
        return getMatrix();
    }


    default boolean hasOptimization()
    {
        return false;
    }


    default Complex[] applyOptimize(Complex[] v)
    {
        return null;
    }


    void setInverse(boolean inv);
}