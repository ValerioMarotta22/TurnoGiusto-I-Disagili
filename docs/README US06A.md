# US06A — Generazione del piano settimanale

In **Turni → Genera piano settimanale** scegli il lunedì dal calendario e seleziona gli eventuali festivi. Premi **Genera bozza** per vedere per ogni servizio i posti richiesti, i dipendenti assegnati e le scoperture. La colonna **Scoperti** mostra un fondo rosso tenue quando mancano persone e verde tenue quando i posti sono tutti coperti.

La bozza raggruppa le righe per giorno e, dentro ogni giorno, per **Pranzo** e **Cena**. Clicca in qualsiasi punto della barra del giorno o del servizio per aprire o richiudere il gruppo, anche sulla freccia. Il testo delle barre rimane nero anche dopo la selezione. I gruppi con turni partono aperti. Puoi aprire anche un giorno o un servizio senza fabbisogni per vedere il relativo messaggio.

Il Service ripete i fabbisogni configurati sui giorni della settimana. Un festivo selezionato usa il proprio fabbisogno anche se cade nel weekend. Gli orari per ruolo sono definiti in `OrariTurni`: cuochi e aiuto cuochi **10:00–15:30 / 17:00–00:00**, camerieri **11:30–16:00 / 18:00–00:30**, lavapiatti **12:00–16:00 / 19:00–00:30**. Le cene terminano il giorno successivo.

Le assegnazioni vengono inizialmente provate in ordine di orario e di ID dipendente. Servono ruolo corretto, contratto valido alla data di inizio del turno e assenza di sovrapposizioni. Per il part-time le fasce ricorrenti devono coprire l'intero turno. Per il full-time si assume la disponibilità di base. L'applicazione usa `conVincoli()`: ogni proposta passa dai controlli US07, compresa la quota part-time per ruolo e servizio. Raggiungere esattamente il monte ore contrattuale è consentito.

Una seconda fase recupera le scoperture: prova aggiunte e sostituzioni su altri turni per liberare una persona assegnabile. Solo quando non recupera altri posti prova spostamenti che aumentano le ore lavorate a parità di posti coperti. Dopo ogni miglioramento riparte dai posti scoperti. Tutti i cambiamenti rispettano disponibilità, turni confinanti e US07; non vengono mai aggiunte persone oltre il fabbisogno. La ricerca è locale, quindi non garantisce l'ottimo globale per ogni combinazione possibile. Il piano resta in memoria fino al salvataggio.

Il fabbisogno standard dei camerieri a cena è **4 nel weekend** e **5 nei festivi**; a pranzo resta 2. Per aggiornare un database esistente, eseguire [la migrazione SQL](../db/migrations/03_camerieri_weekend.sql) senza ricreare il volume. I piani già salvati conservano i loro fabbisogni: per usare il nuovo valore occorre generare una nuova bozza.

Anche US06B controlla le disponibilità quando elenca i candidati e quando aggiunge o sostituisce un dipendente. Nei dati iniziali Sara Neri copre solo la cena del sabato: la fascia di venerdì termina alle 23:00 e non esiste una fascia domenicale. L'assenza di violazioni sul monte ore non rende disponibile una persona fuori dalle sue fasce.

Avvia Docker Desktop per il MySQL condiviso e usa JDK 21 per l'applicazione desktop JavaFX. Maven è gestito dal Wrapper del progetto. Esegui questi comandi da PowerShell.

```powershell
docker compose up -d --wait
.\mvnw.cmd "-Dtest=GenerazioneTurni*Test" test
.\mvnw.cmd javafx:run
```

Per la suite completa usa `.\mvnw.cmd clean verify`. I test della US06A coprono assegnazioni, scoperture, fasce oltre mezzanotte, contratti dalla data richiesta, dati consegnati alla US07 e navigazione JavaFX. [Note per il merge](<README US06A integrazione.md>)
