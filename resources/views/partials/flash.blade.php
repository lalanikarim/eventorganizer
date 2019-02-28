@if (null !== ($flash = session('flash')))

    <div class="alert alert-success" style="position: absolute; top:60px; right:200px" id="flash">
        {{ $flash }} <a href="#" onclick="deleteFlash();">X</a>
    </div>
    <script type="text/javascript">
        function deleteFlash()
        {
            if ($('#flash')) {
                $('#flash').remove();
            }
        }
        setTimeout('deleteFlash()',3000);
    </script>
@endif