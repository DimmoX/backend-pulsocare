package cl.pulsocare.auth.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce errores tecnicos a respuestas HTTP claras. */
@RestControllerAdvice
public class ManejadorErrores {

    /** Ej.: correo duplicado o rol/parentesco inexistente (FK/UK). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Conflicto de integridad");
        pd.setDetail("La operacion viola una restriccion de la base de datos " +
                "(por ejemplo, correo ya registrado o rol inexistente).");
        return pd;
    }
}
