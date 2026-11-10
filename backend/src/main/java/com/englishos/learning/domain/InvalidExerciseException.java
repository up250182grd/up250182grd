package com.englishos.learning.domain;

/** La definición de un ejercicio (prompt/solución) o la respuesta enviada no tiene la forma esperada. */
public class InvalidExerciseException extends RuntimeException {

    public InvalidExerciseException(String message) {
        super(message);
    }
}
