# When does it show?

This app was created to figure out when Google Play shows the "in app purchases" in the google play store.

StackOver Flow question.

https://stackoverflow.com/questions/75377050/when-developing-google-play-billing-subscriptions-what-is-the-trigger-that-puts

Play Store Link

https://play.google.com/store/apps/details?id=com.flesh.questions.whendoesitshow

### The Answer
**TLDR;**

The "In-app Purchases" section in the Play Store shows as soon as you activate the Subscription in the Play console and have the Billing Library Dependency as a Dependency in your App on the Production track. Both must be true and the "In-App Purchases" will disappear if you remove the Dependecny from the app in the production track.

Full Answer in the Stackoverflow Question
