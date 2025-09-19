export const DURATION_PATTERN =
    '(' +
  '(?!$)' +
  '((\\d+d)|(\\d+\\.\\d+d$))?' +
  '((?=\\d)' +
  '((\\d+h)|(\\d+\\.\\d+h$))?' +
  '((\\d+m)|(\\d+\\.\\d+m$))?' +
  '(\\d+(\\.\\d+)?s)?)??' +
  ')+'

export const BACKEND = 'http://127.0.0.1:8080'
