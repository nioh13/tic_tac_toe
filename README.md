# tic_tac_toe
Переносить все файлы лучше в одну папку
Шаги для запуска сервера:
1. Запустить сервер в терминале:
java TicTacToeServer
По умолчанию используется порт 12345.
2. Установить nmap 
Подключиться к серверу с помощью комманды в терминале :
ncat localhost 12345 (хост)
ncat (ваш ip  в туннеле) 12345 (игрок)

После подключения вы увидите приветствие и сможете вводить команды.
   
Пример команд:
1) LIST – показать доступные комнаты и выводом состояния(Ждёт игрока или комната полная)
2) CREATE room1 secret – создать комнату с именем room1 и паролем secret
3) JOIN room1 secret – присоединиться к комнате room1
4) EXIT – выйти из игры

Ходы во время игры задаются в виде двух чисел – номера строки и столбца, например:
2 3
чтобы поставить свой символ во вторую строку, третий столбец.

Пример сессии:

Запуск сервера:
java TicTacToeServer
Подключение первого клиента:
ncat localhost 12345
Ввести логин/пароль при запросе.
Ввести команду: CREATE room1 secret
Подключение второго клиента (в другом терминале):
ncat localhost 12345
Ввести логин/пароль при запросе.
Ввести команду: JOIN room1 secret
После этого начнётся игра, и вы сможете делать ходы, например:
1 1
2 2
3 3

Используя nmap вы сможете подключиться и взаимодействовать  
