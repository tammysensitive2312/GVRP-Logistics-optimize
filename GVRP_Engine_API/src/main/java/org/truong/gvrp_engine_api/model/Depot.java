package org.truong.gvrp_engine_api.model;

import jakarta.websocket.server.ServerEndpoint;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Depot {
    private Long id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
}
