# US06A — Generazione del piano settimanale

In **Turni → Genera piano settimanale** scegli il lunedì dal calendario e seleziona gli eventuali festivi. Premi **Genera bozza** per vedere per ogni servizio i posti richiesti, i dipendenti assegnati e le scoperture. La colonna **Scoperti** mostra un fondo rosso tenue quando mancano persone e verde tenue quando i posti sono tutti coperti.

La bozza raggruppa le righe per giorno e, dentro ogni giorno, per **Pranzo** e **Cena**. Clicca in qualsiasi punto della barra del giorno o del servizio per aprire o richiudere il gruppo, anche sulla freccia. Il testo delle barre rimane nero anche dopo la selezione. I gruppi con turni partono aperti. Puoi aprire anche un giorno o un servizio senza fabbisogni per vedere il relativo messaggio.

Il Service ripete i fabbisogni configurati sui giorni della settimana. Un festivo selezionato usa il proprio fabbisogno anche se cade nel weekend. Gli orari sono fissi: **10:00–16:00** per il pranzo e **17:00–00:30 del giorno successivo** per la cena. Sono mostrati nella schermata e nelle barre dei servizi e vengono usati per generare i turni.

Le assegnazioni vengono provate in ordine di orario e di ID dipendente. Servono ruolo corretto, contratto valido alla data di inizio del turno e assenza di sovrapposizioni. Per il part-time le fasce ricorrenti devono coprire l'intero turno. Per il full-time si assume la disponibilità di base. Il piano rimane in memoria e mostra «Monte ore e riposi da verificare» finché il verificatore US07 non è collegato.

Avvia Docker Desktop per il MySQL condiviso e usa JDK 21 per l'applicazione desktop JavaFX. Maven è gestito dal Wrapper del progetto. Esegui questi comandi da PowerShell.

```powershell
docker compose up -d --wait
.\mvnw.cmd "-Dtest=GenerazioneTurni*Test" test
.\mvnw.cmd javafx:run
```

Per la suite completa usa `.\mvnw.cmd clean verify`. I test della US06A coprono assegnazioni, scoperture, fasce oltre mezzanotte, contratti dalla data richiesta, dati consegnati alla US07 e navigazione JavaFX. [Note per il merge](<README US06A integrazione.md>)
