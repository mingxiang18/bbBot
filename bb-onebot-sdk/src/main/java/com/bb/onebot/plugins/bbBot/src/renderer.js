// 页面加载完成时触发
function onLoad() {
    // WebSocket构造函数，创建WebSocket对象
    window.ws = new WebSocket('ws://localhost:8888')

    window.LLAPI.on("new-messages", (message) => {
        window.ws.send(JSON.stringify(message))
    })

    // 连接成功后的回调函数
    window.ws.onopen = function (params) {
      console.log('客户端连接成功')
    };

    // 从服务器接受到信息时的回调函数
    window.ws.onmessage = function (e) {
      console.log('收到服务器响应', e.data)
      var message = JSON.parse(e.data);
      window.LLAPI.sendMessage(message.peer, message.messageElementList);
    };

    // 连接关闭后的回调函数
    window.ws.onclose = function(evt) {
      console.log("关闭客户端连接");
      window.ws = new WebSocket('ws://localhost:8888')
    };

    // 连接失败后的回调函数
    window.ws.onerror = function (evt) {
      console.log("连接失败了");
    };
}


// 打开设置界面时触发
function onConfigView(view) {

}


// 这两个函数都是可选的
export {
    onLoad
}
