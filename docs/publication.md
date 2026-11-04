# Publier LibreBlock sur Google Play

## 1. Choisir l'identifiant définitif
`applicationId` dans `app/build.gradle.kts` est **définitif dès le premier envoi** sur Google Play.
Vérifie qu'il est libre : `https://play.google.com/store/apps/details?id=<identifiant>` doit renvoyer une erreur 404.

## 2. Créer la clé d'envoi (une seule fois)
Depuis la racine du projet (keytool demande les mots de passe et ton nom) :

```powershell
& "E:\Outil\Intellij\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Copie ensuite `keystore.properties.example` en `keystore.properties` et remplis les mots de passe.
keytool crée un fichier PKCS12 : la clé a le même mot de passe que le fichier, donc `keyPassword` = `storePassword`.
Ces deux fichiers sont ignorés par git. **Sauvegarde-les hors du PC** (gestionnaire de mots de passe, clé USB).
Avec la signature d'applications Google Play, une clé d'envoi perdue peut être réinitialisée par le support Google,
mais c'est long.

## 3. Construire le bundle

```bash
gradlew.bat bundleRelease
```

Résultat : `app/build/outputs/bundle/release/app-release.aab`.
Pour chaque nouvelle version, augmente `versionCode` (et `versionName`) dans `app/build.gradle.kts`.

## 4. Compte et Play Console
1. Crée un compte développeur sur https://play.google.com/console (25 $ une fois, vérification d'identité).
2. *Créer une application* → nom, langue par défaut français, application gratuite.
3. Remplis *Contenu de l'application* avec `docs/play-store.md` : règles de confidentialité, accessibilité,
   sécurité des données, classification, public cible, publicités.
4. Remplis la *Fiche principale du Play Store* (textes et visuels de `docs/play-store.md` et `docs/store/`).

## 5. Test fermé (obligatoire pour un compte personnel récent)
1. *Tests → Test fermé* : crée un canal, envoie le `.aab`, ajoute au moins **12 testeurs** (adresses Gmail).
2. Les testeurs doivent rester inscrits **14 jours d'affilée**.
3. Ensuite, demande l'accès à la production depuis le tableau de bord.

## 6. Production
*Production → Créer une version* → envoie le `.aab` → déploiement. La première revue prend en général
quelques jours, parfois plus à cause de la déclaration d'accessibilité.
