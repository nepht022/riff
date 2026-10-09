#!/usr/bin/env python3
"""Resumo do Macrobenchmark do Riff: p50/p90/p99 de cada métrica de cada teste.

Uso: python3 scripts/resumo_benchmark.py <pasta com *-benchmarkData.json>
"""
import glob
import json
import sys

PERCENTIS = ("P50", "P90", "P99")


def main(pasta):
    arquivos = glob.glob(f"{pasta}/**/*benchmarkData.json", recursive=True)
    if not arquivos:
        sys.exit(f"nenhum resultado em {pasta}")
    for arquivo in arquivos:
        for bench in json.load(open(arquivo))["benchmarks"]:
            print(f"{bench['className'].split('.')[-1]}.{bench['name']}")
            for nome, metrica in bench["metrics"].items():
                print(f"  {nome}: mediana {metrica['median']:.1f}")
            for nome, amostras in bench.get("sampledMetrics", {}).items():
                valores = " ".join(f"{p} {amostras[p]:.1f}" for p in PERCENTIS if p in amostras)
                print(f"  {nome}: {valores}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "baselineprofile/build/outputs")
