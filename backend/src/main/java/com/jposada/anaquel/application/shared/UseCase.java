// by Jeremy Posada
package com.jposada.anaquel.application.shared;

/** Un caso de uso: recibe una intencion y devuelve un resultado. Sin bus ni dispatcher a proposito. */
public interface UseCase<I, O> {
    O handle(I input);
}
