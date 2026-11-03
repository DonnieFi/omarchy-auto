# Notification privacy

The Comms tile reads notifications through Android's
`NotificationListenerService`. The data stays in the app process and is not
sent anywhere.

## What is read

- App label, title, body text, and post time.
- The listener keeps at most eight items in memory for the stage.
- Bodies are never written to logs or files.

## What is hidden

- OTP/2FA and one-time-code text.
- Notifications from package names that look like banking/wallet apps.
- Ongoing, group-summary, transport, progress, and service notifications.
- Notifications whose bodies were redacted as sensitive by Android.

## Allow-list

The default is **allow all non-sensitive apps**. Setup can turn the allow-list
on and choose the apps that may surface. The list appears after those apps have
posted at least one notification; package names are stored locally so the list
survives restarts.

## Dismissal

`CLEAR` and per-notification dismiss only cancel keys currently surfaced in the
stage state. Filtered notifications are never in that state and are not
cancelled.
