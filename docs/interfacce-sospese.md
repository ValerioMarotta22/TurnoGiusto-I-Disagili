# Interfacce US01 e US02 sospese

Il team ha deciso di concentrarsi su model, repository, service e relativi test.
Il lavoro grafico è conservato per poterlo riprendere senza riscriverlo.

## File conservati

Nella cartella `src/main/resources/it/disagili/turnogiusto/ui/`:

- `dipendente-form.fxml.commentato`: modulo di registrazione US01;
- `elenco-dipendenti.fxml.commentato`: elenco dipendenti con apertura della registrazione
  e pulsante di modifica contrattuale ancora da collegare.

Il contenuto originale, inclusi i commenti XML, è rimasto identico. Il suffisso
`.commentato` identifica un file di riferimento: i percorsi `.fxml` usati dai loader
non esistono più, quindi queste schermate non sono caricabili finché non vengono ripristinate.
Non abbiamo avvolto il contenuto in un commento XML: i commenti già presenti non
possono essere annidati dentro un altro commento XML.

I controller `DipendenteFormController` e `ElencoDipendentiController` sono conservati
e continuano a compilare, ma non sono collegati alla finestra principale.
Il FXML e il controller dedicati alla modifica del contratto US02 non erano ancora
stati creati al momento della sospensione.

## Test

`DipendenteFormControllerTest`, comprese le prove dell'elenco al suo interno, è sospeso
con `@Disabled`: questi test richiedono i FXML attivi e sono volutamente saltati.
I test di model, repository e service restano attivi e verificano la logica delle storie.
Non rimuovere o saltare questi ultimi per compensare problemi nelle schermate.

## Come ripristinare

1. Rimuovere soltanto il suffisso `.commentato` dai due file: devono tornare
   `dipendente-form.fxml` ed `elenco-dipendenti.fxml`, nella stessa cartella.
2. Rimuovere l'annotazione `@Disabled` e il relativo import da
   `src/test/java/it/disagili/turnogiusto/ui/DipendenteFormControllerTest.java`.
3. Eseguire ` .\mvnw.cmd clean test` dalla cartella del progetto.
4. Concordare con Valerio l'apertura dell'elenco dalla finestra principale.
   La schermata di modifica US02 resta da realizzare se viene ripresa anche quella parte.

`clean` elimina gli artefatti di compilazione precedenti, che potrebbero contenere
ancora copie dei vecchi FXML. Non elimina i dati MySQL.

## Confini dell'intervento

`hello-view.fxml`, la finestra principale, `pom.xml`, lo schema SQL e i model condivisi
non sono stati modificati per sospendere la UI. La sospensione non completa da sola
l'integrazione US02 con la pianificazione: il verificatore reale dei turni resta da collegare.
