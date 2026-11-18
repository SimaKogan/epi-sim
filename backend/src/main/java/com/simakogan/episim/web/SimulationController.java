package com.simakogan.episim.web;

import com.simakogan.episim.service.SimulationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sim")
public class SimulationController {

    public record ParamsRequest(Double vaccinationRate, Double mutationRate, Double lockdown,
                                Double masks, Double testing, Double speed,
                                Double infectiousness, Double contactRadius, Double infectiousDays,
                                Double fatality, Double vaccineEfficacy) { }

    public record ResetRequest(Integer population, Integer initialInfected, Long seed) { }

    public record InfectRequest(double x, double y, Integer count) { }

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    /** Server-sent events: "world" and "history" once, then a "frame" ~30 times per second. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return service.subscribe();
    }

    @PostMapping("/params")
    public ResponseEntity<Void> params(@RequestBody ParamsRequest r) {
        service.updateParams(r);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pause")
    public ResponseEntity<Void> pause() {
        service.setRunning(false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resume")
    public ResponseEntity<Void> resume() {
        service.setRunning(true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@RequestBody(required = false) ResetRequest r) {
        service.reset(r);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/infect")
    public ResponseEntity<Void> infect(@RequestBody InfectRequest r) {
        service.infect(r.x(), r.y(), r.count() == null ? 5 : r.count());
        return ResponseEntity.noContent().build();
    }
}
