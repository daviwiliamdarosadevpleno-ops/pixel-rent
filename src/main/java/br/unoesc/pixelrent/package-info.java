/**
 * Pixel Rent: sistema desktop de gerenciamento de locação de jogos físicos e digitais.
 *
 * <p>Arquitetura em camadas: {@code ui} (Swing) &rarr; {@code service} &rarr; {@code dao} (JDBC)
 * &rarr; PostgreSQL. As regras de negócio ficam no banco (procedures, functions, triggers e
 * views); o Java as invoca sem duplicá-las.</p>
 */
package br.unoesc.pixelrent;
