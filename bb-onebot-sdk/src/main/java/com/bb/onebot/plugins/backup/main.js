let ws = null;

function onLoad(plugin, liteloader) {
     connectWsServer();
     listenNewMessages();
}

function onBrowserWindowCreated(window, plugin) {

}

module.exports = {
    onLoad,
    onBrowserWindowCreated
}

function connectWsServer() {
    // WebSocket构造函数，创建WebSocket对象
    ws = new WebSocket('ws://localhost:8888')

    // 连接成功后的回调函数
    ws.onopen = function (params) {
      console.log('客户端连接成功')
    };

    // 从服务器接受到信息时的回调函数
    ws.onmessage = function (e) {
      console.log('收到服务器响应', e.data)
      var message = JSON.parse(e.data);
      window.LLAPI.sendMessage(message.peer, message.messageElementList);
    };

    // 连接关闭后的回调函数
    ws.onclose = function(evt) {
      console.log("关闭客户端连接");
      // 进行重连
      setTimeout(function(){
        connectWsServer();
      },2000);
    };

    // 连接失败后的回调函数
    ws.onerror = function (evt) {
      console.log("连接失败了");
    };

    // 监听窗口关闭事件，当窗口关闭时，主动去关闭websocket连接，防止连接还没断开就关闭窗口，这样服务端会抛异常。
    window.onbeforeunload = function() {
        ws.close();
    }
}


function listenNewMessages() {
    LLAPI.on("new-messages", (message) => {
        console.log("接收到消息", message);
        if(ws) {
            ws.send(JSON.stringify(message));
        }
    })
}

function sendMessage(peer, elements) {
//    var peer = await LLAPI.getPeer();
//    const elements = [
//        {
//            type: "text",
//            content: "一条消息"
//        }
//    ]
    await LLAPI.sendMessage(peer, elements);
    console.log("发送消息", elements);
}

function get() {
    var obj = { menu: 'Net'}; //要传的参数
    var xhr = new XMLHttpRequest();  //这里没有考虑IE浏览器，如果需要择if判断加
    xhr.open('GET', "https://www.baidu.com",true);
    xhr.send(JSON.stringify(obj));//这里要是没有参数传，就写null
    xhr.onreadystatechange = function () {
        if (xhr.status === 200 && xhr.readyState === 4) {
            //js处理数据
            console.log(xhr.responseText);
        }
    }
}
