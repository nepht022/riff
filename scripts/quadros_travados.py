#!/usr/bin/env python3
"""Quadros por segundo e quadros travados da partida, lidos dos traces do Perfetto que o Macrobenchmark grava.

O `frameDurationCpuMs` do Macrobenchmark inclui a espera na fila da tela ("Buffer Stuffing": o app adiantado),
então para saber se a partida trava conta-se o que o Android marca como atraso do app (spec do polimento §4).

Uso (com o pacote `perfetto` do Python, por exemplo `uv run --with perfetto`):
    python3 scripts/quadros_travados.py [pasta com *.perfetto-trace]
"""
import glob
import sys

from perfetto.trace_processor import TraceProcessor

PACOTE = "com.rodrigomachado.riff"
CONSULTA = f"""
select count(*) total,
    sum(case when a.jank_type like '%App Deadline Missed%' then 1 else 0 end) atrasados,
    (max(a.ts) - min(a.ts)) / 1e9 segundos
from actual_frame_timeline_slice a join process p using(upid)
where p.name = '{PACOTE}'
"""


def main(pasta):
    traces = sorted(glob.glob(f"{pasta}/**/GameplayBenchmark_*.perfetto-trace", recursive=True))
    if not traces:
        sys.exit(f"nenhum trace da partida em {pasta}")
    for trace in traces:
        processador = TraceProcessor(trace=trace)
        linha = next(iter(processador.query(CONSULTA)))
        fps = linha.total / linha.segundos if linha.segundos else 0
        nome = trace.split("/")[-1].removesuffix(".perfetto-trace")
        print(f"{nome}: {linha.total} quadros, {fps:.0f} fps, {linha.atrasados} com atraso do app")
        processador.close()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "baselineprofile/build/outputs")
