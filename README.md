# xLib
Set of components to build java networked solutions with minimal external dependencies.

There're several base ideas, expressed in standalone or composite items.

1. API: assume API is set of methods and related data structures that are formally described using meta-data and provide execution mechanism. As example, API may represent DBMS as set o tables/views and procedures or java bean (object) with properties and methods. Being formal, these APIs may be easily versioned, verified, compared, published using a variety of techniques.

2. WAMP: API publishing alternative. As standard it provides effective, scalable, flexible way of providing and consuming APIs.

3. DI + CS + Service + Http: data-centric socket channel-oriented networking solution. Base idea is formal representation of data processors handling read/write operations per provider (DI). CS is asynchronous channel selectors light weight engine that provides flexible way of client and/or server centric data processing. Just plug in TCP or UDP handler to process data using DI components. Service component provides abstracted service-oriented data processing model oriented on (but not limited to) request/response paradigm. Sample service implementation is Http (providing limited basic set of HTTP functionality), that also includes support for WebService, REST.

4. WAMP + CS: WAMP transport implementation (WebSocket) for CS infra.

5. OAuth: OAuth protocol extperimental implementations for embedding authentication based on some popular OAUth-enabled providers (Miscrosoft, Google, Facebook, VK, etc.) for use with CS/Http infra.

As result, it is possible to compose solution with various complexity and resources consumption.

Special series is CS, that is mostly sample idea of how to use xLib* components: 
  xLibHttp_CS - simple HTTP service runner with environment/parameters extended configuration support. A kind of reference/base small simple Http service.
  xLibHttpAPI_CS - extension of aove with explicit support for xLibAPI-based APIs publishing.
  xLibWAMPHttpAPI_CS - extension of above with WAMP router/client support and REST/WAMP dynamic bridge.
