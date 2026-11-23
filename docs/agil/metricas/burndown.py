#!/usr/bin/env python3
"""Burndown normalizado de los sprints 1 y 2 (docs/agil/sprint-2.md).

Datos de Jira del 23 de noviembre de 2026: story points (Story point estimate) y fecha de resolución
de cada historia de usuario. Regenera la imagen con:

    python3 docs/agil/metricas/burndown.py
"""
from pathlib import Path
from datetime import datetime, timezone, timedelta
L=timezone(timedelta(hours=-5))
def d(s): return datetime.fromisoformat(s)
s1_start=d("2026-11-03T03:56:34+00:00"); s1_end=d("2026-11-08T05:00:00+00:00"); s1_close=d("2026-11-16T18:09:55+00:00")
s2_start=d("2026-11-15T18:06:10+00:00"); s2_end=d("2026-11-22T05:00:00+00:00"); s2_close=d("2026-11-23T05:51:17+00:00")
S1=[  # HU, puntos, responsable, resolución
("HU-05",5,"David","2026-11-03T22:44:04-05:00"),("HU-06",3,"David","2026-11-05T00:50:30-05:00"),("HU-07",3,"David","2026-11-05T09:56:33-05:00"),
("HU-08",5,"Ismael","2026-11-05T12:14:38-05:00"),("HU-09",3,"Johan","2026-11-05T09:16:09-05:00"),("HU-10",5,"Johan","2026-11-05T09:16:35-05:00"),
("HU-11",5,"Johan","2026-11-06T18:16:59-05:00"),("HU-12",5,"Johan","2026-11-08T14:03:38-05:00"),("HU-13",5,"Ismael","2026-11-07T12:52:57-05:00"),
("HU-14",5,"Johan","2026-11-08T14:03:40-05:00"),("HU-15",3,"David","2026-11-06T17:19:44-05:00"),("HU-16",2,"Johan","2026-11-06T09:29:13-05:00"),
("HU-17",3,"Ismael","2026-11-07T12:53:01-05:00"),("HU-18",13,"David","2026-11-06T22:36:55-05:00"),("HU-19",8,"Jean","2026-11-07T22:13:47-05:00"),
("HU-20",8,"Ismael","2026-11-07T12:53:08-05:00"),("HU-21",2,"Ismael","2026-11-08T19:50:11-05:00"),("HU-22",3,"David","2026-11-06T19:01:39-05:00"),
("HU-23",3,"Jean","2026-11-08T19:50:12-05:00"),("HU-24",2,"Johan","2026-11-08T17:11:50-05:00"),("HU-25",2,"Ismael","2026-11-08T19:50:13-05:00")]
S2=[
("HU-26",8,"Johan","2026-11-19T10:36:33-05:00"),("HU-27",5,"Johan","2026-11-23T00:25:41-05:00"),("HU-28",3,"Johan","2026-11-19T10:36:40-05:00"),
("HU-29",13,"Ismael","2026-11-21T06:05:43-05:00"),("HU-30",5,"Ismael","2026-11-21T06:05:44-05:00"),("HU-31",5,"Ismael","2026-11-21T06:05:45-05:00"),
("HU-32",5,"Johan","2026-11-22T12:14:29-05:00"),("HU-33",3,"Jean","2026-11-18T15:13:21-05:00"),("HU-34",5,"Ismael","2026-11-22T18:53:05-05:00"),
("HU-35",2,"David","2026-11-20T23:11:07-05:00"),("HU-36",5,"Jean","2026-11-16T13:47:45-05:00"),("HU-37",3,"Jean","2026-11-16T13:47:46-05:00"),
("HU-38",5,"Johan","2026-11-22T13:12:39-05:00"),("HU-39",5,"David","2026-11-21T18:57:48-05:00"),("HU-40",5,"David","2026-11-22T02:34:05-05:00"),
("HU-41",3,"David","2026-11-22T18:11:14-05:00"),("HU-42",8,"Jean","2026-11-18T15:13:19-05:00"),("HU-43",5,"Jean","2026-11-21T18:35:56-05:00"),
("HU-44",2,"Jean","2026-11-18T15:16:45-05:00"),("HU-45",5,"Jean","2026-11-18T15:16:45-05:00"),("HU-46",3,"Jean","2026-11-22T18:27:19-05:00"),
("HU-47",3,"Jean","2026-11-22T19:07:03-05:00"),("HU-48",5,"Jean","2026-11-22T18:27:20-05:00"),("HU-49",3,"Jean","2026-11-22T18:27:20-05:00"),
("HU-50",2,"Jean","2026-11-22T19:11:12-05:00"),("HU-51",2,"David","2026-11-22T19:53:21-05:00"),("HU-52",3,"Ismael","2026-11-23T00:36:57-05:00"),
("HU-53",2,"David","2026-11-23T00:34:53-05:00"),("HU-54",5,"David","2026-11-22T20:50:38-05:00"),("HU-55",5,"Johan","2026-11-23T00:26:00-05:00"),
("HU-56",5,"(sin asignar)","2026-11-23T00:28:06-05:00"),("HU-57",0,"Ismael","2026-11-23T00:36:54-05:00"),
("HU-58",8,"David","2026-11-20T23:30:10-05:00"),("HU-59",8,"David","2026-11-20T23:30:14-05:00"),("HU-60",3,"David","2026-11-15T13:27:31-05:00")]
S1=[(a,b,c,d(e)) for a,b,c,e in S1]; S2=[(a,b,c,d(e)) for a,b,c,e in S2]
ADDED={"HU-58","HU-59","HU-60"}

OUT = Path(__file__).resolve().parent.parent / "img" / "burndown-sprints-1-2.png"

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

def series(S, start, end, added=set(), add_time=None):
    init=sum(p for h,p,_,_ in S if h not in added)
    ev=[(r,-p) for h,p,_,r in S]
    if added:
        ev+= [(add_time, sum(p for h,p,_,_ in S if h in added))]
    ev.sort()
    dur=(end-start).total_seconds()
    xs=[0];ys=[100]; cur=init
    for t,dp in ev:
        x=max(0,(t-start).total_seconds()/dur*100)
        xs+= [x,x]; ys+=[cur/init*100,(cur+dp)/init*100]; cur+=dp
    return xs,ys,init

x1,y1,i1=series(S1,s1_start,s1_end)
x2,y2,i2=series(S2,s2_start,s2_end,ADDED,datetime.fromisoformat("2026-11-15T13:25:00-05:00"))
ink="#2b2b29"; muted="#6b6a64"; grid="#e6e5e0"
plt.rcParams.update({"font.family":"DejaVu Sans","font.size":10})
fig,ax=plt.subplots(figsize=(10,5.2),dpi=150)
fig.patch.set_facecolor("#fcfcfb"); ax.set_facecolor("#fcfcfb")
ax.plot([0,100],[100,0],color="#b4b2a9",lw=1.5,ls=(0,(4,3)),label="Ideal")
ax.plot(x1,y1,color="#2a78d6",lw=2,label=f"Sprint 1 ({i1} pts comprometidos)",solid_joinstyle="round")
ax.plot(x2,y2,color="#eb6834",lw=2,label=f"Sprint 2 ({i2} pts comprometidos + 19 agregados)",solid_joinstyle="round")
ax.axvline(100,color=muted,lw=1)
ax.text(100,113,"Fin planeado",ha="center",va="bottom",color=muted,fontsize=9)
ax.text(x1[-1]+1.5,y1[-1]+3,"Sprint 1",color=ink,fontsize=9)
ax.text(x2[-1]+1.5,y2[-1]+9,"Sprint 2",color=ink,fontsize=9)
ax.set_xlim(0,max(x1[-1],x2[-1])+12); ax.set_ylim(0,118)
ax.set_xlabel("Tiempo transcurrido (% de la duración planeada del sprint)",color=muted)
ax.set_ylabel("Trabajo restante (% de lo comprometido)",color=muted)
ax.grid(axis="y",color=grid,lw=0.8); ax.set_axisbelow(True)
for s in ["top","right"]: ax.spines[s].set_visible(False)
for s in ["left","bottom"]: ax.spines[s].set_color(grid)
ax.tick_params(colors=muted)
ax.set_title("Burndown normalizado: Sprint 1 vs Sprint 2 (story points, datos de Jira)",loc="left",color=ink,fontsize=11)
ax.legend(loc="lower left",frameon=False,labelcolor=ink,fontsize=9)
fig.tight_layout()
fig.savefig(OUT,facecolor=fig.get_facecolor())
print(f"Escrito {OUT}")
