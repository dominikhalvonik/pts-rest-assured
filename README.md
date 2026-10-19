# TestLab Shop – Cvičenie 04: REST Assured a testovacie dáta

**Samostatný Maven projekt pre praktickú časť `04_API_testovanie_a_testovacie_data.pptx`.**
Java 21, REST Assured 5.5.7, JUnit Jupiter 5.12.2, Maven Compiler 3.14.0 a Surefire 3.5.3.
Predvolené spustenie obsahuje **tri API testy**, bez Selenium, prehliadača alebo JDBC.
Existujúci PHP/MariaDB backend sa nemení.

## Čo je pripravené

| Metóda v CartQuoteApiTest | Požiadavka | Očakávanie |
|---|---|---|
| `referenceQuote` | KB-001 × 2, MS-001 × 1, STUDENT10, courier | 200; subtotal 8970, discount 897, shipping 490, total 8563 |
| `rejectTextQuantity` | `"quantity": "2"` – text, nie číslo | 422 a `VALIDATION_ERROR` |
| `rejectOutOfStock` | HUB-001 × 1 pri sklade 0 | 409 a `OUT_OF_STOCK` |

Každý test získa vlastný bearer token. V automatickom režime si vytvorí aj nový pracovný priestor,
po teste zruší svoj token a odstráni iba priestor vytvorený týmto konkrétnym testom.
Overuje sa identita účtu a priestoru cez `/auth/me`. Referenčný test kontroluje aj položky ponuky,
menu a to, že API ponuka neznížila sklad. Toto nie je priama nezávislá databázová kontrola.

**Ponuka nie je objednávka.** Tri povinné testy nevytvárajú objednávky, neotvárajú GUI a nič neplatia.

## 1. Otvorenie v IntelliJ

Projekt má importovaný **JUnit BOM 5.12.2** a explicitný `junit-platform-launcher`.
To zosúlaďuje Jupiter 5.12.2 s Platform 1.12.2. Nepridávaj náhodnú inú verziu launcheru.
REST Assured 5.5.7 interne používa Groovy 4.0.22. Preto sú niektoré `groovy-*` knižnice
v Maven závislostiach správne; NEODSTRAŇUJ ich a NEPRIDÁVAJ `groovy-all`, Groovy 6 ani gmavenplus.
Naše zdrojové súbory sú Java, nie Groovy skripty. Ďalší JSON object mapper nie je potrebný:
HTTP payloady odosielame ako JSON text, nie neznámy Java objekt `.body(Map)`.

## 2. Backend

Musí fungovať pôvodná aplikácia TestLab Shop 1.0.0 s MariaDB a `pdo_mysql`.
Najprv na PC over endpoint:

```text
http://127.0.0.1:8080/api/index.php?route=/health
```

Ak backend beží lokálne, v jeho vlastnom priečinku:

```powershell
php -S 127.0.0.1:8080 -t public
```

Použi normálny režim `fault_mode=none`. Neobnovuj databázu ani nemeň režim uprostred testov.
V Java konfigurácii sa zadáva adresa WEBU (`base.url`), napríklad `https://example.test/testlab/`.
Projekt z nej vytvorí `https://example.test/testlab/api/index.php` a operáciu pridá ako `route`.
Podpriečinok sa zachová. Pri desktopových API testoch sa nepoužíva emulátorová adresa `10.0.2.2`.

## 3A. Automatické fixtures – lokálny server vyučujúceho alebo dôveryhodná CI

Vytvor náhodný kľúč v termináli (v PHP prostredí):

```powershell
php -r "echo bin2hex(random_bytes(32)), PHP_EOL;"
```

V existujúcom `app/config.local.php` pôvodného backendu UPRAV tieto položky, nevymieňaj celý
konfiguračný súbor a nemaž sekciu databázového spojenia:

```php
'app_env' => 'testing',
'enable_test_api' => true,
'fault_mode' => 'none',
'api_delay_ms' => 0,
'test_api_key' => 'SEM_SKUTOCNY_VYGENEROVANY_KLUC',
```

V tomto Java projekte je už pripravený `config/test.properties`:

```properties
base.url=http://127.0.0.1:8080/
fixtures.mode=api
timeout.seconds=30
allow.http=true
test.api.key=SEM_SKUTOCNY_ROVNAKY_KLUC_AKO_V_PHP
```

Nahraď zástupný kľúč skutočnou hodnotou. Zvyšok môže zostať pri lokálnom nasadení.
V tomto režime nevypĺňaš študentský e-mail ani heslo: nový účet si každý test získa z fixture API.
`allow.http=true` je výslovné povolenie nezašifrovaného laboratórneho spojenia. Na hostingu používaj HTTPS.

Namiesto uloženia kľúča do súboru možno nastaviť `TEST_API_KEY` v Environment variables
konfigurácie Run v IntelliJ. Nastavenie v jednom PowerShell okne sa automaticky neprenesie
do už spusteného IntelliJ. Kľúč nepíš do JVM argumentov `-D` ani do verejného Gitu.

## 3B. Ručne pridelené fixtures – študenti na spoločnom serveri

Vyučujúci v PHP projekte spustí **trikrát**:

```powershell
php bin/fixtures.php create
```

Príkaz vracia `workspace_code`, `email` a `password` priamo v JSON. Každý test dostane inú trojicu.
V Java projekte skopíruj `config/manual.example.properties` na `config/test.properties` a vyplň:

```properties
fixtures.mode=manual
base.url=http://127.0.0.1:8080/
allow.http=true
timeout.seconds=30
manual.referenceQuote.workspace=SEM_RUN_KOD_1
manual.referenceQuote.email=SEM_EMAIL_1
manual.referenceQuote.password=SEM_HESLO_1
manual.rejectTextQuantity.workspace=SEM_RUN_KOD_2
manual.rejectTextQuantity.email=SEM_EMAIL_2
manual.rejectTextQuantity.password=SEM_HESLO_2
manual.rejectOutOfStock.workspace=SEM_RUN_KOD_3
manual.rejectOutOfStock.email=SEM_EMAIL_3
manual.rejectOutOfStock.password=SEM_HESLO_3
```

Všetky `SEM_*` nahraď skutočnými údajmi. Workspace má tvar `run-` a 24 hexadecimálnych znakov.
Predvolený `demo` nie je izolovaný priestor a konfigurácia ho zámerne odmietne.
Java v tomto režime nikdy nemaže pridelené priestory; zruší iba token, ktorý sama vytvorila.
Netreba zapnúť HTTP fixture API ani odovzdať `X-Test-Key`.

## 4. Spustenie

Najprv v Maven okne IntelliJ vykonaj cieľ:

```text
test-compile
```

Ten kontroluje kompiláciu, nepripája sa k webu. Potom spusti zelenou šípkou triedu:

```text
src/test/java/sk/testlab/cvicenie04/CartQuoteApiTest.java
```

alebo cieľ Maven:

```text
test
```

Pri spúšťaní cez JUnit v IDE má byť Working directory koreň projektu s `pom.xml` a `config/`.
Nejde o aplikáciu s `main()`. Netreba spúšťať Selenium ani inštalovať ChromeDriver.

Terminál s Maven na PATH:

```powershell
mvn test
# alebo
.\run-tests.bat
# len jeden scenár:
mvn test "-Dtest=CartQuoteApiTest#referenceQuote"
# vlastná adresa bez zmeny konfiguračného súboru:
mvn test "-Dbase.url=https://example.test/testlab/"
```

Očakávanie na správnom backendovom nasadení je **3 tests, 0 failures, 0 errors**.
Je to očakávanie, nie tvrdenie o plnom Maven/REST Assured behu vykonanom pri vytvorení ZIP.

## 5. Diagnostika a životný cyklus

Každý test vytvorí `target/artifacts/<metóda>-<uuid>/`:

- `run.properties` – aktuálny krok, workspace, overenie identity, výsledok a upratanie;
- `01-request.json`, `01-response.json`, ... – bezpečné HTTP diagnostické záznamy;
- `failure.txt`, `setup-failure.txt`, `cleanup-failure.txt` podľa fázy zlyhania.

Maven reporty sú v `target/surefire-reports/`. Pri IDE behu je výsledok JUnit aj v okne testov.
Tokeny, heslá, `Authorization`, cookies a `X-Test-Key` sa nezapisujú do HTTP logov.
Celé telá `/auth/*` a `/testing/*` sú vynechané; zvyšné JSON telá sú maskované podľa citlivých polí.
Neplatné/ne-JSON telo sa do HTTP logu neukladá. REST Assured `.log().all()` zámerne nepoužívame.
Snímky z prehliadača v tomto projekte nie sú: testujeme HTTP, nie GUI.

Presmerovania sú vypnuté: nastav priamo konečnú HTTPS adresu. Nemení sa TLS overovanie.
Automatické HTTP retry sú vypnuté, aby sa pri nejasnej odpovedi nevytvorila ďalšia fixture.
Ak create server vykonal, ale odpoveď sa stratila, klient nepozná ID a nemôže ho bezpečne zmazať.
Tento prípad sa označí ako neznámy výsledok, nie ako úspešné upratanie.
Upratanie sa pokúsi vykonať aj po chybe; pri jeho chybe sa zachová pôvodný testovací neúspech.

## 6. Voliteľné: pripraviť účet pre ďalší GUI test

Nástroj `GuiFixtureTool` je doplnok demonštrujúci použitie API na prípravu dát.
Nie je súčasťou troch predvolených testov. Beží iba na požiadanie a vyžaduje režim `api`:

```powershell
mvn test "-Dtest=GuiFixtureTool" "-Dprepare.gui=true"
```

Rovnaké argumenty možno zadať cez Maven integráciu IntelliJ.
Nástroj vytvorí nový priestor, overí prihlásenie a dostupnosť katalógu, zruší svoj API token,
ale **ponechá vlastný priestor pre nasledujúci GUI test**. Zapíše:

```text
target/prepared-gui/run-.../test.properties
```

Tento jediný výstup je zámerne SÚKROMNÝ a **obsahuje e-mail aj heslo**. Nie je to HTTP log.
Je kompatibilný s `config/test.properties` cvičenia 2. Existujúci config najprv zálohuj.
Pre cvičenie 3 priprav nástrojom tri rôzne priestory a vlož údaje do jeho polí `run.1.*` až `run.3.*`.
Zachovaj si workspace kód mimo `target`, pretože `mvn clean` zmaže lokálny súbor, nie dáta na serveri.
Na Linuxe sú prístupové práva súkromných výstupov obmedzené; na Windows skontroluj ACL svojho priečinka.

Po ukončení GUI práce vyučujúci v PHP projekte odstráni PRESNÝ vytvorený priestor:

```powershell
php bin/fixtures.php delete run-SKUTOCNY_KOD
```

Je to nevratné odstránenie jeho účtu a objednávok; nepoužívaj ho pri aktívnych testoch alebo na
priestor inej skupiny. Nástroj nevytvára objednávku namiesto GUI – používateľská akcia sa má stále
vykonať cez Selenium.
