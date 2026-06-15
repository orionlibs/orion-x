package com.orion.quantumcomputing;

import com.orion.quantumcomputing.gate.PermutationGate;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

public final class Complex
{
    public static final Complex ZERO = new Complex(0.d, 0.d);
    public static final Complex ONE = new Complex(1.d, 0.d);
    public static final Complex I = new Complex(0.d, 1.d);
    static final boolean DEBUG = false;
    private static final double HV = 1. / Math.sqrt(2.);
    public static final Complex HC = new Complex(HV, 0.d);
    public static final Complex HCN = new Complex(-HV, 0.d);
    public float r;
    public float i;


    public Complex(double r)
    {
        this(r, 0.d);
    }


    public Complex(double r, double i)
    {
        this.r = (float)r;
        this.i = (float)i;
    }


    public Complex add(Complex b)
    {
        double nr = this.r + b.r;
        double ni = this.i + b.i;
        return new Complex(nr, ni);
    }


    public Complex addr(Complex b)
    {
        this.r = this.r + b.r;
        this.i = this.i + b.i;
        return this;
    }


    public Complex addmulr(Complex a, Complex b)
    {
        double nr = (a.r * b.r) - (a.i * b.i);
        double ni = (a.r * b.i) + (a.i * b.r);
        this.r = (float)(this.r + nr);
        this.i = (float)(this.i + ni);
        return this;
    }


    public Complex min(Complex b)
    {
        double nr = this.r - b.r;
        double ni = this.i - b.i;
        return new Complex(nr, ni);
    }


    public Complex mul(Complex b)
    {
        double nr = (this.r * b.r) - (this.i * b.i);
        double ni = (this.r * b.i) + (this.i * b.r);
        return new Complex(nr, ni);
    }


    public Complex mul(double b)
    {
        return new Complex(this.r * b, this.i * b);
    }


    public double abssqr()
    {
        return (this.r * this.r + this.i * this.i);
    }


    public static Complex[][] identityMatrix(int dim)
    {
        Complex[][] answer = new Complex[dim][dim];
        for(int i = 0; i < dim; i++)
        {
            for(int j = 0; j < dim; j++)
            {
                if(i == j)
                {
                    answer[i][j] = Complex.ONE;
                }
                else
                {
                    answer[i][j] = Complex.ZERO;
                }
            }
        }
        return answer;
    }


    public static Complex[][] tensor(Complex[][] a, Complex[][] b)
    {
        int d1 = a.length;
        int d2 = b.length;
        Complex[][] result = new Complex[d1 * d2][d1 * d2];
        for(int rowa = 0; rowa < d1; rowa++)
        {
            for(int cola = 0; cola < d1; cola++)
            {
                for(int rowb = 0; rowb < d2; rowb++)
                {
                    for(int colb = 0; colb < d2; colb++)
                    {
                        if((a[rowa][cola] == Complex.ZERO) || (b[rowb][colb] == Complex.ZERO))
                        {
                            result[d2 * rowa + rowb][d2 * cola + colb] = Complex.ZERO;
                        }
                        else
                        {
                            result[d2 * rowa + rowb][d2 * cola + colb] = a[rowa][cola].mul(b[rowb][colb]);
                        }
                    }
                }
            }
        }
        return result;
    }


    static int zCount = 0;
    static int nzCount = 0;


    public static Complex[][] mmul(Complex[][] a, Complex[][] b)
    {
        return slowmmul(a, b);
    }


    public static Complex[][] slowmmul(Complex[][] a, Complex[][] b)
    {
        int arow = a.length;
        int acol = a[0].length;
        int brow = b.length;
        int bcol = b[0].length;
        if(acol != brow)
        {
            throw new RuntimeException("#cols a " + acol + " != #rows b " + brow);
        }
        Complex[][] answer = new Complex[arow][bcol];
        for(int i = 0; i < arow; i++)
        {
            for(int j = 0; j < bcol; j++)
            {
                Complex el = Complex.ZERO;
                for(int k = 0; k < acol; k++)
                {
                    el = el.add(a[i][k].mul(b[k][j]));
                }
                answer[i][j] = el;
            }
        }
        return answer;
    }


    public static Complex[][] conjugateTranspose(Complex[][] src)
    {
        int d0 = src.length;
        int d1 = src[0].length;
        Complex[][] answer = new Complex[d1][d0];
        for(int i = 0; i < d0; i++)
        {
            for(int j = 0; j < d1; j++)
            {
                Complex c = src[i][j];
                answer[j][i] = new Complex(c.r, -1 * c.i);
            }
        }
        return answer;
    }


    public static Complex[][] permutate0(Complex[][] matrix, PermutationGate pg)
    {
        Complex[][] p = pg.getMatrix();
        int dim = p.length;
        Complex[][] answer = new Complex[dim][dim];
        for(int i = 0; i < dim; i++)
        {
            int idx = 0;
            while(p[i][idx].equals(Complex.ZERO))
            {
                idx++;
            }
            for(int j = 0; j < dim; j++)
            {
                answer[i][j] = matrix[idx][j];
            }
        }
        return answer;
    }


    public static Complex[][] permutate(PermutationGate pg, Complex[][] matrix)
    {
        int a = pg.getIndex1();
        int b = pg.getIndex2();
        int amask = 1 << a;
        int bmask = 1 << b;
        int dim = matrix.length;
        Complex cp;
        List<Integer> swapped = new LinkedList<>();
        for(int i = 0; i < dim; i++)
        {
            int j = i;
            int x = (amask & i) / amask;
            int y = (bmask & i) / bmask;
            if(x != y)
            {
                j ^= amask;
                j ^= bmask;
                if(!swapped.contains(j))
                {
                    swapped.add(j);
                    swapped.add(i);
                    for(int k = 0; k < dim; k++)
                    {
                        cp = matrix[k][i];
                        matrix[k][i] = matrix[k][j];
                        matrix[k][j] = cp;
                    }
                }
            }
        }
        return matrix;
    }


    public static Complex[][] permutate(Complex[][] matrix, PermutationGate pg)
    {
        int a = pg.getIndex1();
        int b = pg.getIndex2();
        int amask = 1 << a;
        int bmask = 1 << b;
        int dim = matrix.length;
        List<Integer> swapped = new LinkedList<>();
        for(int i = 0; i < dim; i++)
        {
            int j = i;
            int x = (amask & i) / amask;
            int y = (bmask & i) / bmask;
            if(x != y)
            {
                j ^= amask;
                j ^= bmask;
                if(!swapped.contains(j))
                {
                    swapped.add(j);
                    swapped.add(i);
                    Complex[] rowa = matrix[i];
                    matrix[i] = matrix[j];
                    matrix[j] = rowa;
                }
            }
        }
        return matrix;
    }


    public static void printArray(Complex[] ca)
    {
        if(DEBUG)
        {
            printArray(ca, System.err);
        }
    }


    public static void printArray(Complex[] ca, PrintStream ps)
    {
        ps.println("complex[" + ca.length + "]: ");
        for(Complex c : ca)
        {
            ps.println("-> " + c);
        }
    }


    public static void printMatrix(Complex[][] cm)
    {
        printMatrix(cm, System.err);
    }


    public static void dbg(String s)
    {
        if(DEBUG)
        {
            System.err.println("[DBG] " + System.currentTimeMillis() % 1000000 + ": " + s);
        }
    }


    public static void printMatrix(Complex[][] cm, PrintStream ps)
    {
        if(!DEBUG)
        {
            return;
        }
        ps.println("complex[" + cm.length + "]: ");
        for(int idx = 0; idx < cm.length; idx++)
        {
            String row = "row " + idx;
            for(int jdx = 0; jdx < cm.length; jdx++)
            {
                Complex c = cm[idx][jdx];
                row = row + ":" + (c == null ? "NULL!!!!!!" : c.toString());
            }
            ps.println("-> " + row);
        }
    }


    @Override
    public String toString()
    {
        float mr = this.r;
        float mi = this.i;
        if(Math.abs(mr) < 1e-7)
        {
            mr = 0;
        }
        if(Math.abs(mi) < 1e-7)
        {
            mi = 0;
        }
        if(Math.abs(mr) > .999999)
        {
            mr = 1;
        }
        if(Math.abs(mi) > .999999)
        {
            mi = 1;
        }
        return "(" + mr + ", " + mi + ")";
    }
}
