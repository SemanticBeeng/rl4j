package org.deeplearning4j.rl4j.network.ac;

import lombok.Getter;
import org.deeplearning4j.nn.api.NeuralNetwork;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.recurrent.RnnOutputLayer;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * @author rubenfiszel (ruben.fiszel@epfl.ch) on 8/9/16.
 *
 * Standard implementation of ActorCriticCompGraph
 */
public class ActorCriticCompGraph<NN extends ActorCriticCompGraph> implements IActorCritic<NN> {

    final protected ComputationGraph cg;
    @Getter
    final protected boolean recurrent;

    public ActorCriticCompGraph(ComputationGraph cg) {
        this.cg = cg;
        this.recurrent = cg.getOutputLayer(0) instanceof RnnOutputLayer;
    }

    public NeuralNetwork[] getNeuralNetworks() {
        return new NeuralNetwork[] { cg };
    }

    public static ActorCriticCompGraph load(String path) throws IOException {
        return new ActorCriticCompGraph(ModelSerializer.restoreComputationGraph(path));
    }

    public void fit(INDArray input, INDArray[] labels) {
        cg.fit(new INDArray[] {input}, labels);
    }

    public void reset() {
        if (recurrent) {
            cg.rnnClearPreviousState();
        }
    }

    public INDArray[] outputAll(INDArray batch) {
        if (recurrent) {
            return cg.rnnTimeStep(batch);
        } else {
            return cg.output(batch);
        }
    }

    public NN clone() {
        NN nn = (NN)new ActorCriticCompGraph(cg.clone());
        nn.cg.setListeners(cg.getListeners());
        return nn;
    }

    public void copy(NN from) {
        cg.setParams(from.cg.params());
    }

    public Gradient[] gradient(INDArray input, INDArray[] labels) {
        cg.setInput(0, input);
        cg.setLabels(labels);
        cg.computeGradientAndScore();
        Collection<IterationListener> iterationListeners = cg.getListeners();
        if (iterationListeners != null && iterationListeners.size() > 0) {
            for (IterationListener l : iterationListeners) {
                if (l instanceof TrainingListener) {
                    ((TrainingListener) l).onGradientCalculation(cg);
                }
            }
        }
        return new Gradient[] {cg.gradient()};
    }


    public void applyGradient(Gradient[] gradient, int batchSize) {
        if (recurrent) {
            // assume batch sizes of 1 for recurrent networks,
            // since we are learning each episode as a time serie
            batchSize = 1;
        }
        ComputationGraphConfiguration cgConf = cg.getConfiguration();
        int iterationCount = cgConf.getIterationCount();
        cg.getUpdater().update(gradient[0], iterationCount, batchSize);
        cg.params().subi(gradient[0].gradient());
        Collection<IterationListener> iterationListeners = cg.getListeners();
        if (iterationListeners != null && iterationListeners.size() > 0) {
            for (IterationListener listener : iterationListeners) {
                listener.iterationDone(cg, iterationCount);
            }
        }
        cgConf.setIterationCount(iterationCount + 1);
    }

    public double getLatestScore() {
        return cg.score();
    }

    public void save(OutputStream stream) throws IOException {
        ModelSerializer.writeModel(cg, stream, true);
    }

    public void save(String path) throws IOException {
        ModelSerializer.writeModel(cg, path, true);
    }

    public void save(OutputStream streamValue, OutputStream streamPolicy) throws IOException {
        throw new UnsupportedOperationException("Call save(stream)");
    }

    public void save(String pathValue, String pathPolicy) throws IOException {
        throw new UnsupportedOperationException("Call save(path)");
    }
}

