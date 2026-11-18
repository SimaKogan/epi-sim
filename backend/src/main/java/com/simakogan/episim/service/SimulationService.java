package com.simakogan.episim.service;

import com.simakogan.episim.engine.Engine;
import com.simakogan.episim.engine.Params;
import com.simakogan.episim.web.SimulationController.ParamsRequest;
import com.simakogan.episim.web.SimulationController.ResetRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the single shared simulation. All engine access happens on the "sim-loop" thread;
 * HTTP threads only enqueue commands. Frames are pushed to subscribers by a separate sender
 * thread with "latest frame wins" semantics so a slow browser can never stall the simulation.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);
    private static final int FRAME_MS = 33;

    private final ScheduledExecutorService loop = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "sim-loop"));
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> daemon(r, "sim-sender"));
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> latestFrame = new AtomicReference<>();
    private final AtomicBoolean frameQueued = new AtomicBoolean(false);

    // touched only from the sim-loop thread
    private Engine engine;
    private boolean running = true;
    private boolean dirty = true;
    private double tickBudget = 0;

    @PostConstruct
    void start() {
        engine = new Engine(4000, 5, System.nanoTime());
        loop.scheduleAtFixedRate(this::safeTick, 0, FRAME_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        loop.shutdownNow();
        sender.shutdownNow();
        clients.forEach(SseEmitter::complete);
    }

    // ---------------------------------------------------------------- API used by the controller

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter));
        commands.add(() -> {
            String world = engine.worldJson();
            String history = engine.historyJson();
            sender.execute(() -> {
                send(emitter, "world", world);
                send(emitter, "history", history);
            });
            dirty = true;
        });
        return emitter;
    }

    public void updateParams(ParamsRequest r) {
        commands.add(() -> {
            Params c = engine.params();
            engine.setParams(new Params(
                    r.vaccinationRate() != null ? r.vaccinationRate() : c.vaccinationRate(),
                    r.mutationRate() != null ? r.mutationRate() : c.mutationRate(),
                    r.lockdown() != null ? r.lockdown() : c.lockdown(),
                    r.masks() != null ? r.masks() : c.masks(),
                    r.testing() != null ? r.testing() : c.testing(),
                    r.speed() != null ? r.speed() : c.speed(),
                    r.infectiousness() != null ? r.infectiousness() : c.infectiousness(),
                    r.contactRadius() != null ? r.contactRadius() : c.contactRadius(),
                    r.infectiousDays() != null ? r.infectiousDays() : c.infectiousDays(),
                    r.fatality() != null ? r.fatality() : c.fatality(),
                    r.vaccineEfficacy() != null ? r.vaccineEfficacy() : c.vaccineEfficacy()));
            dirty = true;
        });
    }

    public void setRunning(boolean value) {
        commands.add(() -> { running = value; dirty = true; });
    }

    public void reset(ResetRequest r) {
        commands.add(() -> {
            int population = r != null && r.population() != null ? r.population() : engine.population();
            int initial = r != null && r.initialInfected() != null ? r.initialInfected() : 5;
            long seed = r != null && r.seed() != null ? r.seed() : System.nanoTime();
            Params keep = engine.params();
            engine = new Engine(population, initial, seed);
            engine.setParams(keep);
            running = true;
            tickBudget = 0;
            String world = engine.worldJson();
            String history = engine.historyJson();
            sender.execute(() -> {
                broadcast("world", world);
                broadcast("history", history);
            });
            dirty = true;
        });
    }

    public void infect(double x, double y, int count) {
        commands.add(() -> {
            engine.infectNear(x, y, Math.max(1, Math.min(count, 50)));
            dirty = true;
        });
    }

    // ---------------------------------------------------------------- loop

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) { // never let the scheduled task die
            log.error("Simulation tick failed", t);
        }
    }

    private void tick() {
        Runnable cmd;
        while ((cmd = commands.poll()) != null) cmd.run();

        boolean stepped = false;
        if (running) {
            tickBudget += engine.params().speed() * Engine.TICKS_PER_DAY * FRAME_MS / 1000.0;
            int steps = (int) tickBudget;
            tickBudget -= steps;
            for (int i = 0; i < steps; i++) engine.step();
            stepped = steps > 0;
        }
        if ((stepped || dirty) && !clients.isEmpty()) {
            latestFrame.set(engine.frameJson(running));
            dirty = false;
            if (frameQueued.compareAndSet(false, true)) sender.execute(this::pushLatest);
        } else if (clients.isEmpty()) {
            // nobody watching: keep the world alive but do not build JSON
            dirty = true;
        }
    }

    private void pushLatest() {
        frameQueued.set(false);
        String frame = latestFrame.get();
        if (frame != null) broadcast("frame", frame);
    }

    private void broadcast(String event, String data) {
        for (SseEmitter e : clients) send(e, event, data);
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ex) {
            clients.remove(emitter);
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
