@if ($errors->any())
    <div class="alert alert-danger">
        <ul>
            @foreach ($errors->all() as $error)
                <li>{{ $error }}</li>
            @endforeach
        </ul>
    </div>
@endif

@if (null !== ($status = session('status')))
    <div class="alert alert-success">
        {{ $status }}
    </div>
@endif