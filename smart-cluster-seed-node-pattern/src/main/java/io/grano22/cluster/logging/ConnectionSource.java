package io.grano22.cluster.logging;

import lombok.*;

@Data
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConnectionSource {
    private @NonNull String driverClass;
    private @NonNull String url;
    private @NonNull String user;
    private @NonNull String password;
}
