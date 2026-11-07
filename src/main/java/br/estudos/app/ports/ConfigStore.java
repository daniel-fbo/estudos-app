package br.estudos.app.ports;

import br.estudos.app.config.Config;

import java.io.IOException;

public interface ConfigStore {

    /** Lê o config; se não existir, grava e devolve os padrões. */
    Config load() throws IOException;

    void save(Config config) throws IOException;
}
