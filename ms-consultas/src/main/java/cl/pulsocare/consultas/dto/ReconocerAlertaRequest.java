package cl.pulsocare.consultas.dto;

import jakarta.validation.constraints.NotNull;

/** El usuario (medico) que reconoce/atiende una alerta. */
public record ReconocerAlertaRequest(
        @NotNull Long idUsuario
) {}
