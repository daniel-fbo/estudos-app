package br.estudos.app.tasks;

public enum TaskState {
    NA_FILA("Na fila"), RODANDO("Rodando"), CONCLUIDA("Concluída"), FALHOU("Falhou"), CANCELADA("Cancelada");

    private final String label;

    TaskState(String label) { this.label = label; }

    public String label() { return label; }

    public boolean finished() {
        return this == CONCLUIDA || this == FALHOU || this == CANCELADA;
    }
}
