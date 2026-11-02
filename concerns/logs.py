"""
Módulo utilitário para análise de logs de pavimento e aceleração.
Contém funções para varredura de diretório, padronização de esquemas de dados,
cálculo de métricas estatísticas e visualizações gráficas.
"""

from datetime import datetime
from pathlib import Path
import os
import re
from typing import Dict, List, Optional, Tuple, Union

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns


def parse_datetime_from_filename(filename: str) -> Optional[datetime]:
    """Extrai data e hora a partir do nome do arquivo (ex: pavement_log_20261001_115556.csv)."""
    match = re.search(r"(\d{8})_(\d{6})", filename)
    if match:
        date_str, time_str = match.groups()
        try:
            return datetime.strptime(f"{date_str}_{time_str}", "%Y%m%d_%H%M%S")
        except ValueError:
            return None
    return None


def format_bytes(size_bytes: int) -> str:
    """Formata tamanho em bytes para representação legível (B, KB, MB)."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def format_duration(seconds: float) -> str:
    """Formata duração em segundos para mm:ss ou hh:mm:ss."""
    if seconds <= 0:
        return "00:00"
    total_sec = int(round(seconds))
    hours = total_sec // 3600
    minutes = (total_sec % 3600) // 60
    sec = total_sec % 60
    if hours > 0:
        return f"{hours:02d}:{minutes:02d}:{sec:02d}"
    return f"{minutes:02d}:{sec:02d}"


def load_log(filepath: Union[str, Path]) -> pd.DataFrame:
    """
    Carrega e padroniza um arquivo de log CSV para um DataFrame unificado.
    
    Colunas padronizadas de saída:
    - timestamp_ms: int (milissegundos)
    - elapsed_sec: float (tempo decorrido em segundos desde o início)
    - z_accel: float (aceleração no eixo Z em m/s²)
    - accuracy: int (precisão do sensor)
    - lat: float (latitude, se disponível)
    - lon: float (longitude, se disponível)
    - speed_kmh: float (velocidade em km/h, se disponível)
    - speed_acc: float (precisão da velocidade)
    - is_anomaly: bool (se marcado como anomalia)
    """
    path = Path(filepath)
    if not path.exists():
        raise FileNotFoundError(f"Arquivo não encontrado: {path}")

    # Leitura tolerante
    df = pd.read_csv(path)
    if df.empty:
        return pd.DataFrame()

    norm_df = pd.DataFrame()

    # Mapeamento de timestamp
    if "timestampMs" in df.columns:
        norm_df["timestamp_ms"] = pd.to_numeric(df["timestampMs"], errors="coerce")
    elif "timestamp" in df.columns:
        norm_df["timestamp_ms"] = pd.to_numeric(df["timestamp"], errors="coerce")
    else:
        norm_df["timestamp_ms"] = np.arange(len(df))

    norm_df = norm_df.dropna(subset=["timestamp_ms"])
    if norm_df.empty:
        return pd.DataFrame()

    # Tratar timestamps truncados/corrompidos no final do arquivo
    t0 = norm_df["timestamp_ms"].iloc[0]
    valid_ts = norm_df["timestamp_ms"] >= t0
    norm_df = norm_df[valid_ts].copy()

    # Elapsed time
    norm_df["elapsed_sec"] = (norm_df["timestamp_ms"] - t0) / 1000.0

    # Aceleração Z
    if "z_accel" in df.columns:
        norm_df["z_accel"] = pd.to_numeric(df["z_accel"], errors="coerce")
    elif "zAcceleration" in df.columns:
        norm_df["z_accel"] = pd.to_numeric(df["zAcceleration"], errors="coerce")
    else:
        norm_df["z_accel"] = np.nan

    # Acurácia
    if "accuracy" in df.columns:
        norm_df["accuracy"] = df["accuracy"]
    elif "zAccuracy" in df.columns:
        norm_df["accuracy"] = df["zAccuracy"]
    else:
        norm_df["accuracy"] = 3

    # Anomalias
    if "isAnomaly" in df.columns:
        norm_df["is_anomaly"] = df["isAnomaly"].astype(bool)
    else:
        norm_df["is_anomaly"] = False

    # GPS & Velocidade
    lat_col = "lat" if "lat" in df.columns else ("latitude" if "latitude" in df.columns else None)
    lon_col = "lon" if "lon" in df.columns else ("longitude" if "longitude" in df.columns else None)
    speed_col = "speed" if "speed" in df.columns else ("speedMetersPerSecond" if "speedMetersPerSecond" in df.columns else None)
    speed_acc_col = "speed_acc" if "speed_acc" in df.columns else ("speedAccuracy" if "speedAccuracy" in df.columns else None)

    norm_df["lat"] = pd.to_numeric(df[lat_col], errors="coerce") if lat_col else np.nan
    norm_df["lon"] = pd.to_numeric(df[lon_col], errors="coerce") if lon_col else np.nan
    norm_df["speed_kmh"] = pd.to_numeric(df[speed_col], errors="coerce") * 3.6 if speed_col else np.nan
    norm_df["speed_acc"] = pd.to_numeric(df[speed_acc_col], errors="coerce") if speed_acc_col else np.nan

    return norm_df


def scan_logs(target_dir: Union[str, Path] = "logs") -> pd.DataFrame:
    """
    Varre a pasta de logs e retorna um DataFrame resumo com métricas de cada arquivo:
    nome, quantidade de amostras, tamanho, duração, taxa de amostragem, estatísticas de Z, GPS e status.
    """
    dir_path = Path(target_dir)
    if not dir_path.exists():
        # Fallback para pasta atual ou concerns/logs
        if Path("concerns/logs").exists():
            dir_path = Path("concerns/logs")
        elif Path("logs").exists():
            dir_path = Path("logs")
        else:
            dir_path = Path(".")

    csv_files = sorted(dir_path.glob("*.csv"))
    records = []

    for f in csv_files:
        size_bytes = f.stat().st_size
        dt = parse_datetime_from_filename(f.name)
        dt_str = dt.strftime("%Y-%m-%d %H:%M:%S") if dt else "N/A"

        try:
            df = load_log(f)
            total_samples = len(df)
        except Exception as e:
            records.append({
                "Arquivo": f.name,
                "Status": f"Erro: {e}",
                "Amostras": 0,
                "Tamanho": format_bytes(size_bytes),
                "Data/Hora": dt_str,
                "Duração": "00:00",
                "Freq (Hz)": 0.0,
                "Z Médio (m/s²)": np.nan,
                "Z DesvPad": np.nan,
                "Z Mín": np.nan,
                "Z Máx": np.nan,
                "GPS Válidos": 0,
                "Vel Média (km/h)": np.nan,
                "Vel Máx (km/h)": np.nan,
                "Anomalias": 0,
                "Path": str(f)
            })
            continue

        if total_samples == 0:
            records.append({
                "Arquivo": f.name,
                "Status": "Vazio (Sem dados)",
                "Amostras": 0,
                "Tamanho": format_bytes(size_bytes),
                "Data/Hora": dt_str,
                "Duração": "00:00",
                "Freq (Hz)": 0.0,
                "Z Médio (m/s²)": np.nan,
                "Z DesvPad": np.nan,
                "Z Mín": np.nan,
                "Z Máx": np.nan,
                "GPS Válidos": 0,
                "Vel Média (km/h)": np.nan,
                "Vel Máx (km/h)": np.nan,
                "Anomalias": 0,
                "Path": str(f)
            })
        else:
            duration_s = df["elapsed_sec"].iloc[-1] - df["elapsed_sec"].iloc[0]
            freq_hz = (total_samples - 1) / duration_s if duration_s > 0 else 0.0
            
            z = df["z_accel"].dropna()
            z_mean = z.mean() if not z.empty else np.nan
            z_std = z.std() if not z.empty else np.nan
            z_min = z.min() if not z.empty else np.nan
            z_max = z.max() if not z.empty else np.nan

            valid_gps = df[df["lat"].notna() & (df["lat"] != 0)]
            gps_count = len(valid_gps)
            
            speed = df["speed_kmh"].dropna()
            speed_valid = speed[speed >= 0]
            v_mean = speed_valid.mean() if not speed_valid.empty else np.nan
            v_max = speed_valid.max() if not speed_valid.empty else np.nan

            anomalies_count = int(df["is_anomaly"].sum())

            records.append({
                "Arquivo": f.name,
                "Status": "Com dados",
                "Amostras": total_samples,
                "Tamanho": format_bytes(size_bytes),
                "Data/Hora": dt_str,
                "Duração": format_duration(duration_s),
                "Freq (Hz)": round(freq_hz, 1),
                "Z Médio (m/s²)": round(z_mean, 3) if not np.isnan(z_mean) else np.nan,
                "Z DesvPad": round(z_std, 3) if not np.isnan(z_std) else np.nan,
                "Z Mín": round(z_min, 3) if not np.isnan(z_min) else np.nan,
                "Z Máx": round(z_max, 3) if not np.isnan(z_max) else np.nan,
                "GPS Válidos": gps_count,
                "Vel Média (km/h)": round(v_mean, 1) if not np.isnan(v_mean) else np.nan,
                "Vel Máx (km/h)": round(v_max, 1) if not np.isnan(v_max) else np.nan,
                "Anomalias": anomalies_count,
                "Path": str(f)
            })

    return pd.DataFrame(records)


def plot_all_logs(target_dir: Union[str, Path] = "logs", max_cols: int = 2, downsample_threshold: int = 5000):
    """
    Plota as séries temporais de aceleração de todos os logs que possuem amostras.
    Para logs muito extensos, faz amostragem visual otimizada para manter alta performance.
    """
    summary_df = scan_logs(target_dir)
    active_logs = summary_df[summary_df["Amostras"] > 0].copy()
    
    if active_logs.empty:
        print("Nenhum log com dados encontrado para plotagem.")
        return

    n_plots = len(active_logs)
    n_cols = min(max_cols, n_plots)
    n_rows = (n_plots + n_cols - 1) // n_cols

    fig, axes = plt.subplots(n_rows, n_cols, figsize=(7.5 * n_cols, 3.8 * n_rows), sharex=False)
    if n_plots == 1:
        axes = np.array([axes])
    axes_flat = axes.flatten()

    for idx, (_, row) in enumerate(active_logs.iterrows()):
        ax = axes_flat[idx]
        df = load_log(row["Path"])
        
        plot_df = df
        if len(df) > downsample_threshold:
            step = max(1, len(df) // downsample_threshold)
            plot_df = df.iloc[::step]

        ax.plot(plot_df["elapsed_sec"], plot_df["z_accel"], color="#1f77b4", linewidth=0.8, alpha=0.85, label="Z-Accel")
        ax.axhline(0, color="gray", linestyle="--", linewidth=0.7, alpha=0.6)
        
        if "is_anomaly" in df.columns and df["is_anomaly"].any():
            anomalies = df[df["is_anomaly"]]
            ax.scatter(anomalies["elapsed_sec"], anomalies["z_accel"], color="red", s=20, label="Anomalia", zorder=4)

        ax.set_title(f"{row['Arquivo']} ({row['Amostras']:,} amostras | {row['Duração']})", fontsize=11, fontweight="bold")
        ax.set_xlabel("Tempo Decorrido (s)", fontsize=9)
        ax.set_ylabel("Aceleração Z (m/s²)", fontsize=9)
        ax.grid(True, linestyle=":", alpha=0.6)
        ax.legend(loc="upper right", fontsize=8)

    for j in range(n_plots, len(axes_flat)):
        axes_flat[j].set_visible(False)

    plt.tight_layout()
    plt.show()
