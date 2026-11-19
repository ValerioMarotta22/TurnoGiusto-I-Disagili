# US03 e US04 — Disponibilità dei part-time

La US03 gestisce le fasce settimanali dei dipendenti part-time, anche oltre mezzanotte. La US04 le mostra nel riepilogo del titolare, dove gli elenchi lunghi scorrono in verticale.

Avvia Docker Desktop ed esegui questi comandi dalla cartella del progetto. I test di integrazione usano lo stesso MySQL dell’applicazione e rimuovono i propri dati di prova.

```powershell
docker compose up -d --wait
.\mvnw.cmd "-Dtest=*Disponibilita*Test" test
.\mvnw.cmd javafx:run
```
