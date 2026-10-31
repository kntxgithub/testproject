/**
 * かんたん予定追加（ログイン不要版）の配信用スクリプト。
 * 画面（index.html）を表示するだけで、カレンダー等へのアクセス権は使いません。
 * そのため「高度な保護機能プログラム」のアカウントでも承認画面が出ずに動きます。
 */
function doGet() {
  return HtmlService.createHtmlOutputFromFile('index')
    .setTitle('かんたん予定追加')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, maximum-scale=1');
}
