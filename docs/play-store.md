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
> Bloque tes applis immédiatement, de 5 minutes à 8 heures.
>
> 🍅 SESSIONS DE TRAVAIL
> Alterne travail et pauses (par exemple 30 min / 5 min). Pendant les pauses, tes applis sont débloquées et une
> notification te prévient quand le travail reprend.
>
> 📅 HORAIRES
> Programme des blocages récurrents par jour de la semaine, y compris la nuit (ex. 23:00 – 08:00).
> Chaque horaire utilise la liste principale ou sa propre liste d'applis, et peut être souple ou strict.
>
> 🔒 MODE STRICT
> Pendant une session ou un horaire strict, impossible d'arrêter le blocage, de retirer des applis, de désactiver
> LibreBlock ou de le désinstaller. Idéal quand la volonté ne suffit pas.
>
> 🛡 RESPECT DE TA VIE PRIVÉE
> LibreBlock n'a pas accès à Internet et ne collecte aucune donnée. Tes réglages restent sur ton téléphone.
> Le code source est ouvert.
>
> ℹ️ SERVICE D'ACCESSIBILITÉ
> LibreBlock utilise l'API d'accessibilité d'Android (AccessibilityService) uniquement pour savoir quelle appli est
> ouverte et afficher l'écran de blocage, et, en mode strict, pour empêcher la désactivation ou la désinstallation de
> LibreBlock. Aucune donnée n'est collectée ni partagée.

**Catégorie** : Productivité
**Coordonnées** : libreblock.app@gmail.com
**Règles de confidentialité** : https://jvittone.github.io/LibreBlock/privacy

## Visuels
- Icône 512 × 512 : `docs/store/icon-512.png`
- Bannière 1024 × 500 : `docs/store/feature-1024x500.png`
- Captures d'écran téléphone (2 à 8) : `docs/store/screenshot-*.png`

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
