$().ready(function(){
    var vm = new Vue({
        el: '#app',
        data: {
            tableData: [],
            total: 50,
            page_size: 5,
            current_page: 1
        },
        methods: {
            handleEdit: function(row) {
                layer.open({
                    type: 2,
                    title: '状态变更',
                    maxmin: true,
                    shadeClose: false, // 点击遮罩关闭层
                    area: ['800px', '700px'],
                    content: context + 'bugly/exception/update?id='+row.id,
                    end: function () {
                        vm.getDifferentExceptionList();
                    }
                });
            },

            handleDetail:function(row) {
                         $.ajax({
                                url: context + 'bugly/exception/detail',
                                type: 'GET',
                                success: function (res) {
                                }
                            });

//               window.location.href = context + "bugly/exception/detail?id="+row.id;
//                 window.open(context + "bugly/exception/detail?id="+row.id);
            },

            handleSizeChange: function (val) {
                vm.page_size = val;
                this.getDifferentExceptionList();
            },
            handleCurrentChange: function (val) {
                vm.current_page = val;
                this.getDifferentExceptionList();
            },

            getDifferentExceptionList: function () {
                            $.ajax({
                                url: context + 'exception/findAll',
                                type: 'GET',
                                success: function (res) {
                                    vm.tableData = res.data.sysUserList;
                                    vm.total = res.data.total;
                                    vm.page_size = res.data.page_size;
                                    vm.current_page = res.data.page;
                                }
                            });
                        },



            search: function () {
                    var searchBugly = {
                           'machinneAddress':bugly_search.search_machinneAddress.value,
                           'nickName':bugly_search.search_nickName.value,
                           'errorLocaltion':bugly_search.search_errorLocaltion.value,
                           'state':bugly_search.search_state.value
                           };

                $.ajax({
                    cache : true,
                    type : "POST",
                    url : context + 'exception/search',
                    data :JSON.stringify(searchBugly),
                    dataType : 'json',
                    contentType:'application/json',
                    error : function(request) {
                        parent.layer.alert("Connection error");
                    },
                    success : function(res) {
                        vm.tableData = res.data.sysUserList;
                        vm.total = res.data.total;
                        vm.page_size = res.data.page_size;
                        vm.current_page = res.data.page;
                    }
                });
         }


        },
        mounted: function () {
            this.getDifferentExceptionList();
        }
    });
});