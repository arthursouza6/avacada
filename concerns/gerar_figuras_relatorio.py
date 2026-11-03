"""
Script para geração automatizada das figuras técnicas do Relatório AV1 (GAT108).
Remove menções a datas/horas dos nomes de arquivo e gera gráficos em alta resolução.
"""

from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import pandas as pd
from logs import scan_logs, load_log

# Diretório de saída
output_dir = Path("docs/figuras")
output_dir.mkdir(parents=True, exist_ok=True)

# Configuração visual profissional
plt.rcParams['font.sans-serif'] = 'DejaVu Sans'
plt.rcParams['axes.edgecolor'] = '#333333'
plt.rcParams['axes.linewidth'] = 0.8

# Carregar resumo de logs
summary_df = scan_logs("concerns/logs")

# Mapear para nomes técnicos sem dia e hora
nomes_tecnicos = [
    "Ensaio 01 (Urbano Regular)",
    "Ensaio 02 (Trecho Crítico / Anomalia)",
    "Ensaio 03 (Segundo Plano / Longa Duração)",
    "Ensaio 04 (Trecho com Ondulações)",
    "Ensaio 05 (Trecho Curto com Impacto)",
    "Ensaio 06 (Calibração Rápida)",
    "Ensaio 07 (Trecho Contínuo Liso)",
    "Ensaio 08 (Via Arterial Plana)",
    "Ensaio 09 (Tráfego Urbano Fluido)",
    "Ensaio 10 (Percurso Extenso Pavimentado)",
    "Ensaio 11 (Trecho Misto com Solavancos)",
]

summary_df["NomeTecnico"] = nomes_tecnicos[:len(summary_df)]
summary_df["EnsaioID"] = [f"Ensaio {i+1:02d}" for i in range(len(summary_df))]

print("Gerando Figura 1: Séries Temporais de Todos os Ensaios...")
n_plots = len(summary_df)
n_cols = 2
n_rows = (n_plots + n_cols - 1) // n_cols

fig, axes = plt.subplots(n_rows, n_cols, figsize=(15, 3.4 * n_rows), sharex=False)
axes_flat = axes.flatten()

for idx, row in summary_df.iterrows():
    ax = axes_flat[idx]
    df = load_log(row["Path"])
    
    # Downsample suave para performance gráfica mantendo picos extremos
    if len(df) > 8000:
        step = max(1, len(df) // 8000)
        plot_df = df.iloc[::step]
    else:
        plot_df = df
        
    ax.plot(plot_df["elapsed_sec"], plot_df["z_accel"], color="#0055aa", linewidth=0.6, alpha=0.85, label="Aceleração $a_z$")
    ax.axhline(0, color="#666666", linestyle="--", linewidth=0.7, alpha=0.6)
    
    # Destacar anomalia se houver pico severo (> 20 m/s²)
    peaks = plot_df[plot_df["z_accel"].abs() > 20.0]
    if not peaks.empty:
        ax.scatter(peaks["elapsed_sec"], peaks["z_accel"], color="#cc0000", s=30, label="Pico Anômalo", zorder=5)
        
    ax.set_title(f"{row['NomeTecnico']} — {row['Amostras']:,} amostras | {row['Duração']} | {row['Freq (Hz)']} Hz", 
                 fontsize=10, fontweight="bold", pad=4)
    ax.set_xlabel("Tempo Decorrido (s)", fontsize=8)
    ax.set_ylabel("Aceleração Z (m/s²)", fontsize=8)
    ax.grid(True, linestyle=":", alpha=0.5)
    ax.tick_params(labelsize=8)
    if idx == 0:
        ax.legend(loc="upper right", fontsize=7)

for j in range(n_plots, len(axes_flat)):
    axes_flat[j].set_visible(False)

plt.suptitle("Séries Temporais da Aceleração Vertical (Eixo Z) nos 11 Ensaios Experimentais", 
             fontsize=13, fontweight="bold", y=0.998)
plt.tight_layout()
fig1_path = output_dir / "figura1_series_temporais_todos_ensaios.png"
plt.savefig(fig1_path, dpi=200, bbox_inches='tight')
plt.close()
print(f"Salvo: {fig1_path}")


print("Gerando Figura 2: Análise Multivariável Sincronizada (Aceleração Z vs Velocidade GPS)...")
# Usar o ensaio crítico com anomalia real (Ensaio 02 - Amostragem Completa)
df_gps = load_log(summary_df.loc[1, "Path"])
t_max_e2 = df_gps["elapsed_sec"].max()
fig, (ax_acc, ax_spd) = plt.subplots(2, 1, figsize=(14, 6), sharex=True)

# Aceleração Z
ax_acc.plot(df_gps["elapsed_sec"], df_gps["z_accel"], color="#0055aa", linewidth=0.6, alpha=0.85)
ax_acc.axhline(0, color="#666666", linestyle="--", linewidth=0.7, alpha=0.5)
ax_acc.set_title(f"Resposta Vertical da Suspensão ($a_z$) — Amostragem Completa ({len(df_gps):,} amostras | {t_max_e2:.1f}s)", 
                 fontsize=11, fontweight="bold")
ax_acc.set_ylabel("Aceleração Z (m/s²)", fontsize=9)
ax_acc.set_xlim(0, t_max_e2)
ax_acc.grid(True, linestyle=":", alpha=0.5)

# Velocidade
ax_spd.plot(df_gps["elapsed_sec"], df_gps["speed_kmh"], color="#008837", linewidth=1.1)
ax_spd.set_title(f"Velocidade Cinemática do Veículo Medida pelo Receptor GNSS (Média: {df_gps['speed_kmh'].mean():.1f} km/h | Máx: {df_gps['speed_kmh'].max():.1f} km/h)", fontsize=11, fontweight="bold")
ax_spd.set_xlabel("Tempo Decorrido (segundos)", fontsize=9)
ax_spd.set_ylabel("Velocidade (km/h)", fontsize=9)
ax_spd.set_xlim(0, t_max_e2)
ax_spd.grid(True, linestyle=":", alpha=0.5)

plt.tight_layout()
fig2_path = output_dir / "figura2_analise_multivariavel_az_velocidade.png"
plt.savefig(fig2_path, dpi=200, bbox_inches='tight')
plt.close()
print(f"Salvo: {fig2_path}")


print("Gerando Figura 3: Comparativo Geral dos Ensaios (Amostras, Duração, Rugosidade)...")
fig, (ax1, ax2, ax3) = plt.subplots(1, 3, figsize=(15, 5))

# Duração em segundos numéricos
dur_secs = []
for dur_str in summary_df["Duração"]:
    parts = dur_str.split(":")
    dur_secs.append(int(parts[0]) * 60 + int(parts[1]))
summary_df["DuracaoSegundos"] = dur_secs

# 1. Amostras
sns.barplot(data=summary_df, x="Amostras", y="EnsaioID", ax=ax1, color="#2b5c8f")
ax1.set_title("Volume de Amostras por Ensaio", fontweight="bold", fontsize=10)
ax1.set_xlabel("Total de Amostras", fontsize=9)
ax1.set_ylabel("")
ax1.grid(True, axis="x", linestyle=":", alpha=0.5)

# 2. Duração
sns.barplot(data=summary_df, x="DuracaoSegundos", y="EnsaioID", ax=ax2, color="#2ca02c")
ax2.set_title("Duração Efetiva do Ensaio (s)", fontweight="bold", fontsize=10)
ax2.set_xlabel("Segundos", fontsize=9)
ax2.set_ylabel("")
ax2.set_yticklabels([])
ax2.grid(True, axis="x", linestyle=":", alpha=0.5)

# 3. Rugosidade (Desvio Padrão Z)
sns.barplot(data=summary_df, x="Z DesvPad", y="EnsaioID", ax=ax3, color="#d95f02")
ax3.set_title("Dispersão / Rugosidade ($s_z$ em m/s²)", fontweight="bold", fontsize=10)
ax3.set_xlabel("Desvio Padrão Z (m/s²)", fontsize=9)
ax3.set_ylabel("")
ax3.set_yticklabels([])
ax3.grid(True, axis="x", linestyle=":", alpha=0.5)

plt.suptitle("Comparativo Estatístico dos 11 Ensaios de Campo Coletados", fontsize=12, fontweight="bold", y=1.02)
plt.tight_layout()
fig3_path = output_dir / "figura3_comparativo_ensaios.png"
plt.savefig(fig3_path, dpi=200, bbox_inches='tight')
plt.close()
print(f"Salvo: {fig3_path}")


print("Gerando Figura 4: Detecção de Anomalia Crítica em Amostragem Completa...")
# Carregar Ensaio 02 (Amostragem Completa)
df_critico = load_log(summary_df.loc[1, "Path"])
t_max_critico = df_critico["elapsed_sec"].max()
idx_max = df_critico["z_accel"].idxmax()
t_peak = df_critico.loc[idx_max, "elapsed_sec"]
z_peak = df_critico.loc[idx_max, "z_accel"]

fig, (ax_zoom, ax_res) = plt.subplots(2, 1, figsize=(14, 6), sharex=True)

# 1. Sinal do acelerômetro completo
ax_zoom.plot(df_critico["elapsed_sec"], df_critico["z_accel"], color="#0055aa", linewidth=0.6, alpha=0.85, label=r"Aceleração $a_z$")
ax_zoom.axhline(0, color="#666666", linestyle="--", linewidth=0.7, alpha=0.6)
ax_zoom.scatter([t_peak], [z_peak], color="#cc0000", s=45, zorder=5, 
                label=f"Pico Crítico ({z_peak:.2f} m/s² @ t={t_peak:.1f}s)")
ax_zoom.set_title(f"Detecção de Erro Grosseiro em Buraco Real — Amostragem Completa ({len(df_critico):,} amostras | {t_max_critico:.1f}s)", 
                  fontsize=11, fontweight="bold")
ax_zoom.set_ylabel("Aceleração Z (m/s²)", fontsize=9)
ax_zoom.set_xlim(0, t_max_critico)
ax_zoom.grid(True, linestyle=":", alpha=0.5)
ax_zoom.legend(loc="upper right", fontsize=8)

# 2. Resíduo Normalizado Qui-Quadrado completo
v_ms = (df_critico["speed_kmh"] / 3.6).ffill().bfill().fillna(0.0)
r = df_critico["z_accel"] - 0.5 * v_ms
acc_map = {3: 0.25, 2: 1.0, 1: 4.0}
var_az = df_critico["accuracy"].map(acc_map).fillna(1.0)
var_v = (df_critico["speed_acc"].fillna(0.5) ** 2).clip(lower=0.01) if "speed_acc" in df_critico.columns else 0.25
var_r = var_az + 0.25 * var_v
gamma = (r ** 2) / var_r

ax_res.plot(df_critico["elapsed_sec"], gamma, color="#e6550d", linewidth=0.7, label=r"Resíduo Normalizado $\gamma = r^2 / V_r$")
ax_res.axhline(3.84, color="#cc0000", linestyle="--", linewidth=1.5, label=r"Limiar Crítico $\chi^2_{0.95}(1) = 3{,}84$")
ax_res.set_title("Estatística de Teste Qui-Quadrado (Disparo do Alerta de Anomalia / Erro Grosseiro)", 
                 fontsize=11, fontweight="bold")
ax_res.set_xlabel("Tempo Decorrido (segundos)", fontsize=9)
ax_res.set_ylabel(r"Estatística $\gamma$", fontsize=9)
ax_res.set_yscale("log")
ax_res.set_xlim(0, t_max_critico)
ax_res.grid(True, linestyle=":", alpha=0.5)
ax_res.legend(loc="upper right", fontsize=8)

plt.tight_layout()
fig4_path = output_dir / "figura4_zoom_deteccao_anomalia.png"
plt.savefig(fig4_path, dpi=200, bbox_inches='tight')
plt.close()
print(f"Salvo: {fig4_path}")

print("Todas as figuras foram geradas com sucesso!")
