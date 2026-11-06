# Fiche Google Play et formulaires

Textes prêts à copier dans la Play Console.

## Fiche principale

**Nom de l'application** (30 caractères max) :
> LibreBlock – Bloqueur d'applis

**Description courte** (80 caractères max) :
> Bloque les applis qui te distraient. Horaires, pauses, mode strict. Gratuit.

**Description complète** :
> LibreBlock t'aide à reprendre le contrôle de ton téléphone en bloquant les applis qui te font perdre du temps.
> Gratuit, sans publicité, sans compte et sans accès à Internet.
>
> ⏱ SESSIONS DE CONCENTRATION
> Bloque tes applis tout de suite, de 5 minutes à 8 heures. Une notification affiche le temps restant, et une tuile
> des réglages rapides lance ou arrête une session en un geste.
>
> 🍅 SESSIONS DE TRAVAIL
> Alterne travail et pauses (25 min / 5 min par défaut, réglable). Pendant les pauses, tes applis sont débloquées et une
> notification te prévient quand le travail reprend.
>
> 📅 HORAIRES
> Programme des blocages récurrents par jour de la semaine, y compris la nuit (ex. 23:00 – 08:00).
> Chaque horaire utilise la liste principale ou sa propre liste d'applis, et peut être souple ou strict.
>
> 🏖 VACANCES
> Indique tes périodes de vacances : les horaires marqués « Travail » sont suspendus, les autres continuent
> (le blocage du soir, par exemple).
>
> 🚫 TOUT BLOQUER SAUF
> Plutôt que de choisir les applis à bloquer, choisis celles qui restent autorisées : tout le reste est bloqué.
>
> 🔒 MODE STRICT
> Pendant une session ou un horaire strict, impossible d'arrêter le blocage, de retirer des applis, de désactiver
> LibreBlock ou de le désinstaller. Changer l'heure du téléphone ne permet pas de le contourner.
> Idéal quand la volonté ne suffit pas.
>
> 🛟 DES GARDE-FOUS
> Impossible de bloquer complètement ton téléphone : l'écran d'accueil et l'appli Téléphone restent toujours
> accessibles, les appels passent toujours, et les horaires stricts laissent au moins 2 heures libres par jour.
>
> 📊 STATISTIQUES
> Temps de concentration, ouvertures bloquées, série de jours et applis les plus bloquées sur 7 jours.
>
> 🛡 RESPECT DE TA VIE PRIVÉE
> LibreBlock n'a pas accès à Internet et ne collecte aucune donnée. Tes réglages et tes statistiques restent sur ton
> téléphone. C'est un logiciel libre (GPL-3.0) : le code source est ouvert.
>
> ℹ️ SERVICE D'ACCESSIBILITÉ
> LibreBlock utilise l'API d'accessibilité d'Android (AccessibilityService) uniquement pour savoir quelle appli est
> ouverte et afficher l'écran de blocage, et, en mode strict, pour empêcher la désactivation ou la désinstallation de
> LibreBlock. Aucune donnée n'est collectée ni partagée.

### Traduction anglaise
Dans *Fiche principale → Gérer les traductions → Ajouter des traductions → English (United States)*,
puis éventuellement English (United Kingdom) avec les mêmes textes.

**App name**:
> LibreBlock – App Blocker

**Short description** (80 characters max):
> Block distracting apps. Schedules, work breaks, strict mode. Free, no ads.

**Full description**:
> LibreBlock helps you take back control of your phone by blocking the apps that waste your time.
> Free, no ads, no account and no Internet access.
>
> ⏱ FOCUS SESSIONS
> Block your apps right away, from 5 minutes to 8 hours. A notification shows the time left, and a Quick Settings
> tile starts or stops a session in one tap.
>
> 🍅 WORK SESSIONS
> Alternate work and breaks (25 min / 5 min by default, adjustable). During breaks your apps are unblocked, and a
> notification tells you when it's time to get back to work.
>
> 📅 SCHEDULES
> Set up recurring blocks by day of the week, including overnight (e.g. 11:00 PM – 8:00 AM).
> Each schedule uses the main list or its own list of apps, and can be flexible or strict.
>
> 🏖 VACATIONS
> Add your vacation periods: schedules marked "Work" are suspended, the others keep running (your evening block,
> for example).
>
> 🚫 BLOCK ALL EXCEPT
> Instead of picking the apps to block, pick the ones you allow: everything else is blocked.
>
> 🔒 STRICT MODE
> During a strict session or schedule, you can't stop blocking, remove apps, disable LibreBlock or uninstall it.
> Changing the phone's clock won't get you around it. Perfect for when willpower isn't enough.
>
> 🛟 SAFEGUARDS
> You can never lock yourself out of your phone: the home screen and the Phone app always stay available, calls
> always get through, and strict schedules leave at least 2 free hours every day.
>
> 📊 STATS
> Focus time, blocked openings, daily streak and most blocked apps over 7 days.
>
> 🛡 YOUR PRIVACY
> LibreBlock has no Internet access and collects no data. Your settings and stats stay on your phone.
> It's free software (GPL-3.0): the source code is open.
>
> ℹ️ ACCESSIBILITY SERVICE
> LibreBlock uses the Android accessibility API (AccessibilityService) only to know which app is open and show the
> block screen, and, in strict mode, to prevent LibreBlock from being disabled or uninstalled. No data is collected
> or shared.

**Catégorie** : Productivité
**Coordonnées** : libreblock.app@gmail.com
**Règles de confidentialité** : https://jvittone.github.io/LibreBlock/privacy

## Visuels
- Icône 512 × 512 : `docs/store/icon-512.png`
- Bannière 1024 × 500 : `docs/store/feature-1024x500.png` (français), `docs/store/feature-1024x500-en.png` (anglais)
- Captures d'écran téléphone : `docs/store/play/fr/*.png` pour la fiche française, `docs/store/play/en/*.png` pour
  la fiche anglaise. Play refuse un grand côté de plus du double du petit : ces versions sont élargies par
  `tools/play-screenshots.py` à partir de `docs/store/fr` et `docs/store/en` (1080 × 2424).
- Visuels régénérables avec `tools/store-graphics.ps1`

## Déclaration « Utilisation de l'API AccessibilityService »
Dans *Contenu de l'application → Accessibilité* :
- **L'appli est-elle un outil d'accessibilité ?** Non (`isAccessibilityTool="false"`).
- **Fonctionnalité principale** : blocage d'applications pour le bien-être numérique et la concentration.
- **Justification** :
  > LibreBlock is a digital wellbeing app that blocks apps chosen by the user. The AccessibilityService is used to
  > detect the app in the foreground (TYPE_WINDOW_STATE_CHANGED) and display a block screen when that app is on the
  > user's block list during an active session or schedule. In optional strict mode, it reads the text of Settings and
  > package installer screens to close those that would disable or uninstall LibreBlock before the user-defined
  > session ends. No data is collected, stored or transmitted; the app has no Internet permission. A prominent
  > disclosure explaining this use is shown in the app and the user must tap "J'accepte" before being sent to the
  > accessibility settings.
- **Vidéo** (lien YouTube non répertorié) montrant : ouverture de l'appli → bouton « Activer le blocage » →
  écran d'information → « J'accepte » → activation dans les réglages → choix d'une appli → session → l'appli est bloquée.

## Sécurité des données (Data safety)
- L'appli collecte-t-elle ou partage-t-elle des types de données ? **Non**.
- Les données sont-elles chiffrées en transit ? Sans objet (aucune transmission).
- Les utilisateurs peuvent-ils demander la suppression ? Sans objet ; tout est supprimé à la désinstallation.

## Classification du contenu
Questionnaire IARC : catégorie « Utilitaire, productivité, communication ou autre ». Réponse « Non » à toutes
les questions (pas de violence, de contenu sexuel, de jeux d'argent, d'échanges entre utilisateurs, etc.).

## Public cible
13 ans et plus (évite les exigences spécifiques aux applis pour enfants).

## Autres déclarations
- Publicités : **Non**.
- Accès à l'application : **toutes les fonctionnalités sont disponibles sans restriction**.
- Appli d'actualité, appli santé, appli gouvernementale : **Non**.
